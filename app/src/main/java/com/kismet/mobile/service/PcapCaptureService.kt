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
        private const val NOTIF_ID = 42
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
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

        startForeground(NOTIF_ID, buildNotif("Capturing packets…"))

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
        outputFile?.let { f -> if (f.exists() && f.length() > 0) shareCapture(f) }
        super.onDestroy()
    }

    private fun shareCapture(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, "Open PCAP in…")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun createNotifChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "PCAP Capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(text: String): Notification {
        val stopIntent = Intent(this, PcapCaptureService::class.java).apply { action = "STOP" }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kismet Capture")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_device_generic)
            .addAction(0, "STOP", stopPi)
            .build()
    }
}
