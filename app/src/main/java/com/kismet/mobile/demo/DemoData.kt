package com.kismet.mobile.demo

import com.kismet.mobile.model.Campaign
import com.kismet.mobile.model.CampaignStatus
import com.kismet.mobile.model.CampaignType
import com.kismet.mobile.model.DataSource
import com.kismet.mobile.model.DataSourceDriver
import com.kismet.mobile.model.Device
import com.kismet.mobile.model.Dot11Device
import com.kismet.mobile.model.PhyStat
import com.kismet.mobile.model.SignalRecord
import com.kismet.mobile.model.SystemStatus

object DemoData {

    val devices: List<Device>
        get() {
            val now = System.currentTimeMillis() / 1000
            return listOf(
                // ── WiFi APs ─────────────────────────────────────────────────
        Device(
            key = "demo-k01", mac = "AA:BB:CC:11:22:01",
            name = "CoffeeShop_Guest", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "Cisco Systems", channel = "6",
            signal = SignalRecord(-62, -78, -55),
            totalPackets = 12_480, dataPackets = 8_200,
            lastTime = now - 4, firstTime = now - 7200,
            dot11 = Dot11Device(numClients = 7, numSsids = 1),
        ),
        Device(
            key = "demo-k02", mac = "AA:BB:CC:11:22:02",
            name = "HomeNetwork_5G", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "Netgear", channel = "36",
            signal = SignalRecord(-48, -65, -44),
            totalPackets = 84_320, dataPackets = 61_000,
            lastTime = now - 2, firstTime = now - 86400,
            dot11 = Dot11Device(numClients = 3, numSsids = 2),
        ),
        Device(
            key = "demo-k03", mac = "AA:BB:CC:11:22:03",
            name = "Airport_Free_WiFi", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "Ubiquiti Networks", channel = "11",
            signal = SignalRecord(-70, -82, -65),
            totalPackets = 320_000, dataPackets = 240_000,
            lastTime = now - 12, firstTime = now - 43200,
            dot11 = Dot11Device(numClients = 34, numSsids = 1),
        ),
        Device(
            key = "demo-k04", mac = "AA:BB:CC:11:22:04",
            name = "Office_Secure_5GHz", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "Cisco Systems", channel = "149",
            signal = SignalRecord(-52, -60, -48),
            totalPackets = 950_000, dataPackets = 720_000,
            lastTime = now - 1, firstTime = now - 172800,
            dot11 = Dot11Device(numClients = 12, numSsids = 1),
        ),
        Device(
            key = "demo-k05", mac = "AA:BB:CC:11:22:05",
            name = "Neighbor_IoT", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "TP-Link", channel = "1",
            signal = SignalRecord(-78, -88, -72),
            totalPackets = 5_400, dataPackets = 3_100,
            lastTime = now - 30, firstTime = now - 14400,
            dot11 = Dot11Device(numClients = 1, numSsids = 1),
        ),
        Device(
            key = "demo-k06", mac = "AA:BB:CC:11:22:06",
            name = "HotelLobby_WiFi", phyName = "IEEE 802.11", type = "AP",
            manufacturer = "Aruba Networks", channel = "40",
            signal = SignalRecord(-66, -75, -61),
            totalPackets = 180_000, dataPackets = 130_000,
            lastTime = now - 8, firstTime = now - 21600,
            dot11 = Dot11Device(numClients = 18, numSsids = 2),
        ),
        // ── Bluetooth devices ─────────────────────────────────────────
        Device(
            key = "demo-k07", mac = "BB:CC:DD:11:22:01",
            name = "iPhone 15 Pro", phyName = "Bluetooth",
            manufacturer = "Apple, Inc.", channel = "",
            signal = SignalRecord(-62, -78, -58),
            totalPackets = 42, lastTime = now - 6, firstTime = now - 3600,
        ),
        Device(
            key = "demo-k08", mac = "BB:CC:DD:11:22:02",
            name = "Galaxy Buds2 Pro", phyName = "Bluetooth",
            manufacturer = "Samsung Electronics", channel = "",
            signal = SignalRecord(-72, -85, -68),
            totalPackets = 18, lastTime = now - 14, firstTime = now - 1800,
        ),
        Device(
            key = "demo-k09", mac = "BB:CC:DD:11:22:03",
            name = "MacBook Pro", phyName = "Bluetooth",
            manufacturer = "Apple, Inc.", channel = "",
            signal = SignalRecord(-55, -68, -50),
            totalPackets = 130, lastTime = now - 3, firstTime = now - 7200,
        ),
        Device(
            key = "demo-k10", mac = "BB:CC:DD:11:22:04",
            name = "JBL Flip 6", phyName = "Bluetooth",
            manufacturer = "JBL", channel = "",
            signal = SignalRecord(-80, -92, -75),
            totalPackets = 8, lastTime = now - 45, firstTime = now - 900,
        ),
        Device(
            key = "demo-k11", mac = "BB:CC:DD:11:22:05",
            name = "Pixel Watch 2", phyName = "Bluetooth",
            manufacturer = "Google", channel = "",
            signal = SignalRecord(-68, -78, -62),
            totalPackets = 55, lastTime = now - 9, firstTime = now - 5400,
        ),
            )
        }
    val systemStatus: SystemStatus
        get() = SystemStatus(
            deviceCount = 247,
            memoryRss = 48_200,
            timestampSec = System.currentTimeMillis() / 1000,
            batteryPct = -1,
        )

    val phyStats = listOf(
        PhyStat(name = "IEEE 802.11", deviceCount = 142, packetCount = 1_420_000),
        PhyStat(name = "Bluetooth",   deviceCount = 105, packetCount = 45_000),
    )

    val sources = listOf(
        DataSource(
            uuid = "demo-uuid-wlan", name = "wlan0mon",
            typeDriver = DataSourceDriver("linuxwifi"), interfaceName = "wlan0mon",
            hardware = "Alpha AWUS036ACH",
            running = 1, error = 0, packets = 1_420_000,
        ),
        DataSource(
            uuid = "demo-uuid-hci", name = "hci0",
            typeDriver = DataSourceDriver("linuxbluetooth"), interfaceName = "hci0",
            hardware = "Intel Bluetooth Adapter",
            running = 1, error = 0, packets = 45_000,
        ),
    )

    val campaigns: List<Campaign>
        get() = listOf(
            Campaign(
                id = "demo-camp-1",
                name = "Downtown Survey",
                type = CampaignType.SURVEY,
                description = "Public WiFi audit — city centre",
                status = CampaignStatus.ACTIVE,
                createdAt = System.currentTimeMillis() - 86_400_000,
                startedAt = System.currentTimeMillis() - 3_600_000,
                deviceKeys = mutableListOf("demo-k01", "demo-k03", "demo-k06"),
            ),
            Campaign(
                id = "demo-camp-2",
                name = "Rogue AP Hunt",
                type = CampaignType.HUNT,
                description = "Detect unauthorized access points on corp SSID",
                status = CampaignStatus.DRAFT,
                createdAt = System.currentTimeMillis() - 172_800_000,
            ),
            Campaign(
                id = "demo-camp-3",
                name = "BT Proximity Log",
                type = CampaignType.WARDRIVING,
                description = "Log all Bluetooth devices in range",
                status = CampaignStatus.PAUSED,
                createdAt = System.currentTimeMillis() - 259_200_000,
                startedAt = System.currentTimeMillis() - 7_200_000,
                deviceKeys = mutableListOf("demo-k07", "demo-k08", "demo-k09", "demo-k10", "demo-k11"),
            ),
        )

    // Fake local WiFi/BT shown by LocalDeviceFragment in demo mode
    const val DEMO_WIFI_SSID    = "Demo_Network_5G"
    const val DEMO_WIFI_BSSID   = "AA:BB:CC:DD:EE:FF"
    const val DEMO_WIFI_IP      = "192.0.2.42"  // RFC 5737 example IP (documentation range)
    const val DEMO_WIFI_SIGNAL  = "-54 dBm  (4/5)"
    const val DEMO_WIFI_CHANNEL = "Ch 36  (5 GHz)"
    const val DEMO_WIFI_SPEED   = "867 Mbps"

    const val DEMO_BT_STATE     = "ON"
    const val DEMO_BT_NAME      = "Demo Phone"
    const val DEMO_BT_ADDRESS   = "BB:BB:BB:00:00:01"
    const val DEMO_BT_PAIRED    = "3 paired devices"
    const val DEMO_BT_LIST      = "iPhone 15 Pro  AA:BB:CC:11:22:01\nGalaxy Buds2 Pro  BB:CC:DD:11:22:02\nMacBook Pro  BB:CC:DD:11:22:03"
}
