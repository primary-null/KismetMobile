package com.kismet.mobile.model

import com.google.gson.annotations.SerializedName

data class SystemStatus(
    @SerializedName("kismet.system.devices.count") val deviceCount: Int = 0,
    @SerializedName("kismet.system.memory.rss") val memoryRss: Long = 0,
    @SerializedName("kismet.system.timestamp.sec") val timestampSec: Long = 0,
    @SerializedName("kismet.system.battery.percentage") val batteryPct: Int = -1,
)

data class PhyStat(
    @SerializedName("kismet.phy.phy_name") val name: String = "",
    @SerializedName("kismet.phy.device_count") val deviceCount: Int = 0,
    @SerializedName("kismet.phy.packet_count") val packetCount: Long = 0,
)

// kismet.datasource.type_driver is a nested object in Kismet 2025+
data class DataSourceDriver(
    @SerializedName("kismet.datasource.driver.type") val type: String = ""
)

data class DataSource(
    @SerializedName("kismet.datasource.uuid") val uuid: String = "",
    @SerializedName("kismet.datasource.name") val name: String = "",
    @SerializedName("kismet.datasource.type_driver") val typeDriver: DataSourceDriver? = null,
    @SerializedName("kismet.datasource.interface") val interfaceName: String = "",
    @SerializedName("kismet.datasource.hardware") val hardware: String = "",
    @SerializedName("kismet.datasource.running") val running: Int = 0,
    @SerializedName("kismet.datasource.paused") val paused: Int = 0,
    @SerializedName("kismet.datasource.error") val error: Int = 0,
    @SerializedName("kismet.datasource.num_packets") val packets: Long = 0,
) {
    val type: String get() = typeDriver?.type ?: ""
}

// frequency_map is keyed by frequency string in Kismet 2025+
data class ChannelData(
    @SerializedName("kismet.channeltracker.frequency_map") val channelMap: Map<String, Channel> = emptyMap()
) {
    val channels: List<Channel> get() = channelMap.values.toList()
}

data class ChannelSignal(
    @SerializedName("kismet.common.signal.last_signal") val lastSignal: Int = 0
)

data class ChannelRrd(
    @SerializedName("kismet.common.rrd.last_value") val lastValue: Long = 0
)

data class Channel(
    @SerializedName("kismet.channelrec.channel") val channel: String = "",
    @SerializedName("kismet.channelrec.frequency") val frequencyKhz: Long = 0,
    @SerializedName("kismet.channelrec.signal") val signalData: ChannelSignal? = null,
    @SerializedName("kismet.channelrec.packets_rrd") val packetsRrd: ChannelRrd? = null,
) {
    val signal: Int get() = signalData?.lastSignal ?: 0
    val packets: Long get() = packetsRrd?.lastValue ?: 0L
    val channelLabel: String get() = channel.ifEmpty { "${frequencyKhz / 1000} MHz" }
}

data class PhyList(
    @SerializedName("kismet.device.phytypes") val phys: List<PhyType> = emptyList()
)

data class PhyType(
    @SerializedName("kismet.phy.name") val name: String = "",
    @SerializedName("kismet.phy.devices") val deviceCount: Int = 0,
    @SerializedName("kismet.phy.packets") val packets: Long = 0,
)
