package com.kismet.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.kismet.mobile.R
import com.kismet.mobile.api.KismetClient
import com.kismet.mobile.repository.KismetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PcapCaptureService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var outputFile: File? = null

    companion object {
        const val CHANNEL_ID = "pcap_capture"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_DEVICE_KEY = "device_key"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val ACTION_STOP = "STOP"
        private const val NOTIF_ID = 42
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIF_ID, buildNotif("Capturing packets…"))
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        val uuid = intent?.getStringExtra(EXTRA_UUID)
        val deviceKey = intent?.getStringExtra(EXTRA_DEVICE_KEY)
        val host = intent?.getStringExtra(EXTRA_HOST) ?: "localhost"
        val port = intent?.getIntExtra(EXTRA_PORT, 2501) ?: 2501
        val user = intent?.getStringExtra(EXTRA_USER) ?: ""
        val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""

        val file = File(cacheDir, "kismet_capture_${System.currentTimeMillis()}.pcapng")
        outputFile = file

        job = scope.launch {
            try {
                val api = KismetClient.build(host, port, user, pass)
                val repo = KismetRepository(api)
                FileOutputStream(file).use { out ->
                    repo.streamPcap(out, uuid, deviceKey)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        val file = outputFile
        if (file != null && file.exists() && file.length() > 0) {
            notifyCaptureReady(file)
        }
        super.onDestroy()
    }

    private fun notifyCaptureReady(file: File) {
        try {
            stopForeground(STOP_FOREGROUND_DETACH)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Open PCAP in…")
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                chooser,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Capture ready")
                .setContentText("Tap to view or share ${file.name}")
                .setSmallIcon(R.drawable.ic_device_generic)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotifChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "PCAP Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(text: String): Notification {
        val stopIntent = Intent(this, PcapCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kismet Capture")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_device_generic)
            .addAction(0, ACTION_STOP, stopPi)
            .build()
    }
}
