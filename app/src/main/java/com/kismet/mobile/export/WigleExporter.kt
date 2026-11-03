package com.kismet.mobile.export

import com.kismet.mobile.model.Device
import com.kismet.mobile.viewmodel.KismetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WigleExporter {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun toCsv(
        devices: List<Device>,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("WigleWifi-1.4,appRelease=KismetApp,model=Android,release=0,device=KismetApp,display=,board=,brand=")
        sb.appendLine("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type")
        devices.filter { KismetViewModel.isWifi(it.phyName) }.forEach { d ->
            val ssid = d.displayName.replace(",", "").replace("\n", "")
            val firstSeen = sdf.format(Date(d.firstTime * 1000))
            val rssi = d.signal?.lastSignal ?: 0
            sb.appendLine("${d.mac},${ssid},[WPA2][WPA][ESS],${firstSeen},${d.channel},${rssi},${latitude},${longitude},0,0,WIFI")
        }
        devices.filter { KismetViewModel.isBluetooth(it.phyName) }.forEach { d ->
            val name = d.displayName.replace(",", "")
            val firstSeen = sdf.format(Date(d.firstTime * 1000))
            val rssi = d.signal?.lastSignal ?: 0
            sb.appendLine("${d.mac},${name},[BT],${firstSeen},,${rssi},${latitude},${longitude},0,0,BT")
        }
        return sb.toString()
    }
}
