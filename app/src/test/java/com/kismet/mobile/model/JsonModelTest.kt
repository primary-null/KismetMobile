package com.kismet.mobile.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonModelTest {
    private val gson = Gson()

    @Test fun `explicit null strings fall back to MAC for device display`() {
        val device = gson.fromJson("""{
            "kismet.device.base.macaddr":"AA:BB:CC:DD:EE:FF",
            "kismet.device.base.name":null,
            "kismet.device.base.username":null,
            "kismet.device.base.commonname":null
        }""", Device::class.java)
        assertEquals("AA:BB:CC:DD:EE:FF", device.displayName)
    }

    @Test fun `missing null and partial coordinates are safe`() {
        for (json in listOf("{}", """{"kismet.common.location.geopoint":null}""", """{"kismet.common.location.geopoint":[]}""")) {
            val point = gson.fromJson(json, GeoPoint::class.java)
            assertEquals(0.0, point.lat, 0.0)
            assertEquals(0.0, point.lon, 0.0)
        }
        val point = gson.fromJson("""{"kismet.common.location.geopoint":[12.5]}""", GeoPoint::class.java)
        assertEquals(12.5, point.lon, 0.0)
        assertEquals(0.0, point.lat, 0.0)
    }
}
