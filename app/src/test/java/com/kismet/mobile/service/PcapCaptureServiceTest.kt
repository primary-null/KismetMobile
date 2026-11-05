package com.kismet.mobile.service

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class PcapCaptureServiceTest {

    @org.junit.Test
    fun `repeated foreground start is promoted without launching another capture`() {
        val job = kotlinx.coroutines.Job()
        val service = serviceWith(job)
        val intent = io.mockk.mockk<android.content.Intent>()
        io.mockk.every { intent.action } returns null
        service.onStartCommand(intent, 0, 2)
        io.mockk.verify(exactly = 1) { service.startForeground(42, any()) }
        io.mockk.verify(exactly = 0) { intent.getStringExtra(any()) }
        job.cancel()
    }

    @org.junit.Test
    fun `cancelled capture retains ownership until its cleanup is completed`() {
        val job = io.mockk.mockk<kotlinx.coroutines.Job>()
        io.mockk.every { job.isCompleted } returns false
        io.mockk.every { job.isActive } returns false
        val service = serviceWith(job)
        val intent = io.mockk.mockk<android.content.Intent>()
        io.mockk.every { intent.action } returns null
        service.onStartCommand(intent, 0, 3)
        io.mockk.verify(exactly = 1) { service.startForeground(42, any()) }
        io.mockk.verify(exactly = 0) { intent.getStringExtra(any()) }
    }

    private fun serviceWith(job: kotlinx.coroutines.Job): PcapCaptureService {
        val service = io.mockk.spyk(PcapCaptureService(), recordPrivateCalls = true)
        val notification = io.mockk.mockk<android.app.Notification>()
        io.mockk.every { service["buildNotif"](any<String>()) } returns notification
        io.mockk.every { service.startForeground(any(), any()) } returns Unit
        PcapCaptureService::class.java.getDeclaredField("job").apply {
            isAccessible = true
            set(service, job)
        }
        return service
    }

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
