package com.kismet.mobile.export

import com.kismet.mobile.model.Device
import com.kismet.mobile.model.SignalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WigleExporterTest {

    @Test
    fun `toCsv correctly formats wifi and bluetooth devices`() {
        val wifiDevice = Device(
            key = "WIFI1",
            mac = "00:11:22:33:44:55",
            name = "Test WiFi",
            phyName = "IEEE802.11",
            signal = SignalRecord(lastSignal = -55),
            firstTime = 1622505600, // 2021-06-01 00:00:00 UTC
            channel = "6"
        )
        
        val btDevice = Device(
            key = "BT1",
            mac = "AA:BB:CC:DD:EE:FF",
            name = "Test BT",
            phyName = "Bluetooth",
            signal = SignalRecord(lastSignal = -80),
            firstTime = 1622505600
        )

        val csv = WigleExporter.toCsv(listOf(wifiDevice, btDevice), latitude = 40.0, longitude = -73.0)

        assertTrue(csv.startsWith("WigleWifi-1.4,appRelease=KismetApp,model=Android,release=0,device=KismetApp,display=,board=,brand="))
        assertTrue(csv.contains("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"))
        
        // Assert WiFi line
        assertTrue(csv.contains("00:11:22:33:44:55,Test WiFi,[WPA2][WPA][ESS],"))
        assertTrue(csv.contains(",6,-55,40.0,-73.0,0,0,WIFI"))
        
        // Assert BT line
        assertTrue(csv.contains("AA:BB:CC:DD:EE:FF,Test BT,[BT],"))
        assertTrue(csv.contains(",-80,40.0,-73.0,0,0,BT"))
    }
}
