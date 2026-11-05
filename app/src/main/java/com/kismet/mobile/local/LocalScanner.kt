package com.kismet.mobile.local

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class LocalSighting(
    val mac: String,
    val name: String,
    val rssi: Int,
    val type: SightingType,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class SightingType { WIFI, BT_CLASSIC, BT_LE }

class LocalScanner(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun scanWifi(): List<LocalSighting> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return emptyList()
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val done = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    done.complete(Unit)
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        try {
            if (wm.startScan()) {
                withTimeoutOrNull(8000) { done.await() }
            }
        } finally {
            context.unregisterReceiver(receiver)
        }

        return wm.scanResults.mapNotNull { sr ->
            val bssid = sr.BSSID ?: return@mapNotNull null
            LocalSighting(
                mac  = bssid.uppercase(),
                name = sr.SSID.orEmpty().ifEmpty { "<hidden>" },
                rssi = sr.level,
                type = SightingType.WIFI,
            )
        }
    }

    @SuppressLint("MissingPermission", "InlinedApi")
    suspend fun scanBluetooth(): List<LocalSighting> {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = bm.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        val hasBleScan = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        val hasConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        if (!hasBleScan) {
            Log.w("LocalScanner", "BLUETOOTH_SCAN not granted — skipping BT scan")
            return emptyList()
        }

        val results = java.util.concurrent.CopyOnWriteArrayList<LocalSighting>()
        val done = CompletableDeferred<Unit>()

        // BLE scan — LOW_LATENCY for maximum result density during the survey window
        val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (hasConnect) {
                    results.add(LocalSighting(
                        mac  = result.device.address.uppercase(),
                        name = try { result.device.name ?: "" } catch (_: SecurityException) { "" },
                        rssi = result.rssi,
                        type = SightingType.BT_LE,
                    ))
                }
            }
            override fun onBatchScanResults(scanResults: MutableList<ScanResult>) {
                if (hasConnect) scanResults.forEach { result ->
                    results.add(LocalSighting(
                        mac  = result.device.address.uppercase(),
                        name = try { result.device.name ?: "" } catch (_: SecurityException) { "" },
                        rssi = result.rssi,
                        type = SightingType.BT_LE,
                    ))
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e("LocalScanner", "BLE scan failed: errorCode=$errorCode")
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val leScanner = adapter.bluetoothLeScanner

        // Classic BT discovery — requires RECEIVER_EXPORTED for system broadcasts on API 33+
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (dev != null && hasConnect) {
                            try {
                                results.add(LocalSighting(
                                    mac  = dev.address.uppercase(),
                                    name = dev.name ?: "",
                                    rssi = rssi,
                                    type = SightingType.BT_CLASSIC,
                                ))
                            } catch (_: SecurityException) {}
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> done.complete(Unit)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        try {
            leScanner?.startScan(null, scanSettings, leCallback)
            adapter.startDiscovery()
            withTimeoutOrNull(12_000) { done.await() }
        } finally {
            context.unregisterReceiver(receiver)
            try { leScanner?.stopScan(leCallback) } catch (_: SecurityException) { }
            try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
        }

        return results.distinctBy { it.mac }
    }
}
