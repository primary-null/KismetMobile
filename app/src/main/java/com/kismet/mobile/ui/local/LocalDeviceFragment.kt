package com.kismet.mobile.ui.local

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.kismet.mobile.BuildConfig
import com.kismet.mobile.databinding.FragmentLocalDeviceBinding
import com.kismet.mobile.demo.DemoData
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.nio.ByteOrder

class LocalDeviceFragment : Fragment() {

    private var _binding: FragmentLocalDeviceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private var systemBarTop = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionBanner()
        if (hasRequiredPermissions()) loadAll()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTop = bars.top
            v.setPadding(0, bars.top, 0, v.paddingBottom)
            insets
        }

        windowViewModel.foldingFeature.observe(viewLifecycleOwner) { fold ->
            applyFoldPadding(fold)
        }

        binding.btnRefresh.setOnClickListener { loadAll() }
        binding.btnGrantPermissions.setOnClickListener { requestNeededPermissions() }
        binding.btnScanMatch.setOnClickListener { runSecondLatch() }

        if (BuildConfig.IS_DEMO) {
            binding.layoutPermissionBanner.visibility = View.GONE
            loadAll()
            return
        }

        if (hasRequiredPermissions()) {
            loadAll()
        } else {
            requestNeededPermissions()
        }
        updatePermissionBanner()
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        return perms.toTypedArray()
    }

    private fun hasRequiredPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNeededPermissions() = permissionLauncher.launch(requiredPermissions())

    private fun updatePermissionBanner() {
        binding.layoutPermissionBanner.visibility =
            if (hasRequiredPermissions()) View.GONE else View.VISIBLE
    }

    private fun loadAll() {
        loadWifi()
        loadBluetooth()
    }

    private fun loadWifi() {
        if (BuildConfig.IS_DEMO) {
            binding.tvWifiSsid.text    = DemoData.DEMO_WIFI_SSID
            binding.tvWifiBssid.text   = DemoData.DEMO_WIFI_BSSID
            binding.tvWifiIp.text      = DemoData.DEMO_WIFI_IP
            binding.tvWifiSignal.text  = DemoData.DEMO_WIFI_SIGNAL
            binding.tvWifiChannel.text = DemoData.DEMO_WIFI_CHANNEL
            binding.tvWifiSpeed.text   = DemoData.DEMO_WIFI_SPEED
            return
        }
        val wm = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val info = wm.connectionInfo
        if (info == null || info.networkId == -1) {
            binding.tvWifiSsid.text = "Not connected"
            binding.tvWifiBssid.text = "—"
            binding.tvWifiIp.text = "—"
            binding.tvWifiSignal.text = "—"
            binding.tvWifiChannel.text = "—"
            binding.tvWifiSpeed.text = "—"
            return
        }

        binding.tvWifiSsid.text = info.ssid.removePrefix("\"").removeSuffix("\"")
        binding.tvWifiBssid.text = info.bssid?.uppercase() ?: "—"
        binding.tvWifiIp.text = intToIp(info.ipAddress)
        binding.tvWifiSignal.text = "${info.rssi} dBm  (${WifiManager.calculateSignalLevel(info.rssi, 5)}/5)"
        binding.tvWifiSpeed.text = "${info.linkSpeed} Mbps"

        val freq = info.frequency
        binding.tvWifiChannel.text = when {
            freq in 2400..2500 -> "Ch ${freqToChannel2G(freq)}  (2.4 GHz)"
            freq in 5000..5900 -> "Ch ${freqToChannel5G(freq)}  (5 GHz)"
            freq in 5925..7125 -> "Ch ${freqToChannel6G(freq)}  (6 GHz)"
            else -> "$freq MHz"
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds", "InlinedApi")
    private fun loadBluetooth() {
        if (BuildConfig.IS_DEMO) {
            binding.tvBtState.text      = DemoData.DEMO_BT_STATE
            binding.tvBtName.text       = DemoData.DEMO_BT_NAME
            binding.tvBtAddress.text    = DemoData.DEMO_BT_ADDRESS
            binding.tvBtPaired.text     = DemoData.DEMO_BT_PAIRED
            binding.tvBtPairedList.text = DemoData.DEMO_BT_LIST
            binding.tvBtPairedList.visibility = View.VISIBLE
            return
        }
        val bm = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter

        if (adapter == null) {
            binding.tvBtState.text = "Not available"
            binding.tvBtName.text = "—"
            binding.tvBtAddress.text = "—"
            binding.tvBtPaired.text = "—"
            return
        }

        binding.tvBtState.text = when (adapter.state) {
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "Turning on..."
            BluetoothAdapter.STATE_TURNING_OFF -> "Turning off..."
            else -> "Unknown"
        }

        val hasConnect = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        binding.tvBtName.text = if (hasConnect) adapter.name ?: "—" else "Permission needed"
        binding.tvBtAddress.text = if (hasConnect) adapter.address ?: "—" else "—"

        if (hasConnect) {
            val paired = adapter.bondedDevices ?: emptySet()
            binding.tvBtPaired.text = "${paired.size} paired devices"
            val list = paired.joinToString("\n") { "${it.name}  ${it.address}" }
            binding.tvBtPairedList.text = list.ifEmpty { "None" }
            binding.tvBtPairedList.visibility = View.VISIBLE
        } else {
            binding.tvBtPaired.text = "—"
            binding.tvBtPairedList.visibility = View.GONE
        }
    }

    private fun intToIp(ip: Int): String {
        val ordered = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
            Integer.reverseBytes(ip) else ip
        return InetAddress.getByAddress(
            ByteArray(4) { i -> (ordered shr (24 - i * 8) and 0xFF).toByte() }
        ).hostAddress ?: "—"
    }

    private fun freqToChannel2G(freq: Int) = (freq - 2412) / 5 + 1
    private fun freqToChannel5G(freq: Int) = (freq - 5000) / 5
    private fun freqToChannel6G(freq: Int) = (freq - 5940) / 5

    private fun runSecondLatch() {
        if (BuildConfig.IS_DEMO) {
            Snackbar.make(binding.root, "Matched 3 device(s) seen by both phone and server", Snackbar.LENGTH_LONG).show()
            return
        }
        Snackbar.make(binding.root, "Scanning… this takes ~12s", Snackbar.LENGTH_LONG).show()
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.runLocalScan()
            val count = viewModel.localConfirmCount()
            Snackbar.make(binding.root, "Matched $count device(s) seen by both phone and server", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun applyFoldPadding(fold: FoldingFeature?) {
        binding.root.doOnLayout {
            val bottomPad = if (fold?.state == FoldingFeature.State.HALF_OPENED &&
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                val viewTop = IntArray(2).also { binding.root.getLocationInWindow(it) }[1]
                (binding.root.height - (fold.bounds.top - viewTop)).coerceAtLeast(0)
            } else 0
            binding.root.setPadding(0, systemBarTop, 0, bottomPad)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
