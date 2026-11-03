package com.kismet.mobile.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class SeenByRecord(
    @SerializedName("kismet.common.seenby.uuid") val uuid: String = ""
) : Parcelable

@Parcelize
data class Device(
    @SerializedName("kismet.device.base.key") val key: String = "",
    @SerializedName("kismet.device.base.macaddr") val mac: String = "",
    @SerializedName("kismet.device.base.name") val name: String = "",
    @SerializedName("kismet.device.base.username") val userName: String = "",
    @SerializedName("kismet.device.base.commonname") val commonName: String = "",
    @SerializedName("kismet.device.base.type") val type: String = "",
    @SerializedName("kismet.device.base.phyname") val phyName: String = "",
    @SerializedName("kismet.device.base.signal") val signal: SignalRecord? = null,
    @SerializedName("kismet.device.base.first_time") val firstTime: Long = 0,
    @SerializedName("kismet.device.base.last_time") val lastTime: Long = 0,
    @SerializedName("kismet.device.base.packets.total") val totalPackets: Long = 0,
    @SerializedName("kismet.device.base.packets.data") val dataPackets: Long = 0,
    @SerializedName("kismet.device.base.packets.llc") val llcPackets: Long = 0,
    @SerializedName("kismet.device.base.datasize") val dataSize: Long = 0,
    @SerializedName("kismet.device.base.manuf") val manufacturer: String = "",
    @SerializedName("kismet.device.base.channel") val channel: String = "",
    @SerializedName("kismet.device.base.frequency") val frequency: Double = 0.0,
    @SerializedName("kismet.device.base.location") val location: LocationRecord? = null,
    @SerializedName("kismet.device.base.seenby") val seenBy: List<SeenByRecord> = emptyList(),
    @SerializedName("dot11.device") val dot11: Dot11Device? = null,
) : Parcelable {
    val displayName: String
        get() = (userName ?: "").ifEmpty { (commonName ?: "").ifEmpty { (name ?: "").ifEmpty { mac ?: "" } } }

    val isWifiAp: Boolean
        get() = (phyName ?: "").contains("802.11", ignoreCase = true) &&
                ((type ?: "").contains("AP", ignoreCase = true) || (dot11?.numClients ?: 0) > 0)
}

@Parcelize
data class SignalRecord(
    @SerializedName("kismet.common.signal.last_signal") val lastSignal: Int = 0,
    @SerializedName("kismet.common.signal.min_signal") val minSignal: Int = 0,
    @SerializedName("kismet.common.signal.max_signal") val maxSignal: Int = 0,
) : Parcelable

@Parcelize
data class LocationRecord(
    @SerializedName("kismet.common.location.avg_loc") val avgLocation: GeoPoint? = null,
) : Parcelable

@Parcelize
data class GeoPoint(
    @SerializedName("kismet.common.location.geopoint") val geopoint: List<Double> = emptyList()
) : Parcelable {
    val lon: Double get() = geopoint.getOrElse(0) { 0.0 }
    val lat: Double get() = geopoint.getOrElse(1) { 0.0 }
}

@Parcelize
data class Dot11Device(
    @SerializedName("dot11.device.num_associated_clients") val numClients: Int = 0,
    @SerializedName("dot11.device.num_advertised_ssids") val numSsids: Int = 0,
    @SerializedName("dot11.device.last_bssid") val lastBssid: String = "",
    @SerializedName("dot11.device.typeset") val typeSet: Int = 0,
    @IgnoredOnParcel
    @SerializedName("dot11.device.associated_client_map")
    val associatedClientMap: Map<String, Any>? = null,
) : Parcelable
