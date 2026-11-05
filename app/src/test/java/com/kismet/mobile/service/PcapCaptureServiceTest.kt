package com.kismet.mobile.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class PcapCaptureServiceTest {

    @Test
    fun `test PcapCaptureService constants and intent actions`() {
        assertEquals("uuid", PcapCaptureService.EXTRA_UUID)
        assertEquals("device_key", PcapCaptureService.EXTRA_DEVICE_KEY)
        assertEquals("host", PcapCaptureService.EXTRA_HOST)
        assertEquals("port", PcapCaptureService.EXTRA_PORT)
        assertEquals("user", PcapCaptureService.EXTRA_USER)
        assertEquals("pass", PcapCaptureService.EXTRA_PASS)
        assertEquals("STOP", PcapCaptureService.ACTION_STOP)
    }
}
