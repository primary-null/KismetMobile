package com.kismet.mobile.local

import com.kismet.mobile.model.Device

object SecondLatchMatcher {
    fun match(sightings: List<LocalSighting>, devices: List<Device>): Map<String, LocalSighting> {
        val sightingMap = sightings.associateBy { it.mac.uppercase().replace(":", "") }
        return devices.mapNotNull { device ->
            val normalMac = (device.mac ?: "").uppercase().replace(":", "")
            sightingMap[normalMac]?.let { device.key to it }
        }.toMap()
    }
}
