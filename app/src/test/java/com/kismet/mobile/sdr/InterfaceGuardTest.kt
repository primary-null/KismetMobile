package com.kismet.mobile.sdr

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import com.kismet.mobile.model.DataSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceGuardTest {

    @Test
    fun `check returns Warning if source interface matches active network interface`() {
        val mockContext = mockk<Context>()
        val mockCm = mockk<ConnectivityManager>()
        val mockNetwork = mockk<Network>()
        val mockLinkProps = mockk<LinkProperties>()

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        every { mockCm.activeNetwork } returns mockNetwork
        every { mockCm.getLinkProperties(mockNetwork) } returns mockLinkProps
        every { mockLinkProps.interfaceName } returns "wlan0"

        val source = DataSource(uuid = "123", name = "wlan0", interfaceName = "wlan0")

        val result = InterfaceGuard.check(mockContext, source, "192.168.1.1")

        assertTrue(result is GuardResult.Warning)
        val warning = result as GuardResult.Warning
        assertTrue(warning.reason.contains("wlan0"))
    }

    @Test
    fun `check returns Safe if source interface does not match active network interface`() {
        val mockContext = mockk<Context>()
        val mockCm = mockk<ConnectivityManager>()
        val mockNetwork = mockk<Network>()
        val mockLinkProps = mockk<LinkProperties>()

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockCm
        every { mockCm.activeNetwork } returns mockNetwork
        every { mockCm.getLinkProperties(mockNetwork) } returns mockLinkProps
        every { mockLinkProps.interfaceName } returns "rmnet0" // cellular

        val source = DataSource(uuid = "123", name = "wlan0", interfaceName = "wlan0")

        val result = InterfaceGuard.check(mockContext, source, "192.168.1.1")

        assertEquals(GuardResult.Safe, result)
    }
}
