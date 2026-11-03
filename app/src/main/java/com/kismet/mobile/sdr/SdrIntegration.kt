package com.kismet.mobile.sdr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class SdrApp(
    val name: String,
    val packageName: String,
    val description: String,
    val playStoreId: String = packageName,
)

object SdrIntegration {

    val knownApps = listOf(
        SdrApp(
            name = "RF Analyzer",
            packageName = "com.mantz_it.rfanalyzer",
            description = "Open source. Supports HackRF, RTL-SDR. Spectrum + waterfall.",
        ),
        SdrApp(
            name = "SDR Touch",
            packageName = "com.sdrtouch.rtlsdr",
            description = "Popular RTL-SDR viewer. FM, AM, SSB, CW demodulation.",
        ),
        SdrApp(
            name = "RTL-SDR Driver",
            packageName = "marto.rtl_tcp_andro",
            description = "RTL-SDR USB driver + RTL-TCP server for other apps.",
        ),
        SdrApp(
            name = "SDR++",
            packageName = "com.sdrpp.sdrpp",
            description = "Cross-platform SDR. HackRF + RTL-SDR support.",
        ),
        SdrApp(
            name = "CubicSDR",
            packageName = "com.cubicsdr.cubicsdr",
            description = "Open source wideband SDR receiver.",
        ),
    )

    fun findInstalled(pm: PackageManager): List<SdrApp> =
        knownApps.filter { app ->
            try {
                pm.getPackageInfo(app.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    fun launch(context: Context, app: SdrApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun openPlayStore(context: Context, app: SdrApp) {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${app.playStoreId}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openKismetWebUi(context: Context, host: String, port: Int) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$host:$port"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
