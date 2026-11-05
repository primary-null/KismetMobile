package com.kismet.mobile.export

import com.kismet.mobile.model.Device
import com.kismet.mobile.viewmodel.KismetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WigleExporter {
    fun toCsv(
        devices: List<Device>,
        latitude: Double? = null,
        longitude: Double? = null,
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val sb = StringBuilder()
        sb.appendLine("WigleWifi-1.4,appRelease=KismetApp,model=Android,release=0,device=KismetApp,display=,board=,brand=")
        sb.appendLine("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type")
        devices.filter { KismetViewModel.isWifi(it.phyName) }.forEach { d ->
            val ssid = csvField(d.displayName)
            val firstSeen = sdf.format(Date(d.firstTime * 1000))
            val rssi = d.signal?.lastSignal ?: 0
            val lat = latitude ?: d.location?.avgLocation?.lat ?: 0.0
            val lon = longitude ?: d.location?.avgLocation?.lon ?: 0.0
            // Kismet's device model here does not expose authentication details.
            sb.appendLine("${csvField(d.mac.orEmpty())},${ssid},,${firstSeen},${csvField(d.channel.orEmpty())},${rssi},${lat},${lon},0,0,WIFI")
        }
        devices.filter { KismetViewModel.isBluetooth(it.phyName) }.forEach { d ->
            val name = csvField(d.displayName)
            val firstSeen = sdf.format(Date(d.firstTime * 1000))
            val rssi = d.signal?.lastSignal ?: 0
            val lat = latitude ?: d.location?.avgLocation?.lat ?: 0.0
            val lon = longitude ?: d.location?.avgLocation?.lon ?: 0.0
            sb.appendLine("${csvField(d.mac.orEmpty())},${name},[BT],${firstSeen},,${rssi},${lat},${lon},0,0,BT")
        }
        return sb.toString()
    }

    private fun csvField(value: String): String {
        val singleLine = value.replace('\r', ' ').replace('\n', ' ')
        return if (singleLine.any { it == ',' || it == '"' })
            "\"${singleLine.replace("\"", "\"\"")}\"" else singleLine
    }
}
