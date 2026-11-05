package com.kismet.mobile.sdr

import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.transport.Transport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket

class SshTunnelTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        val sshClientField = SshTunnel::class.java.getDeclaredField("sshClient")
        sshClientField.isAccessible = true
        sshClientField.set(SshTunnel, null)

        val localPortField = SshTunnel::class.java.getDeclaredField("localPort")
        localPortField.isAccessible = true
        localPortField.set(SshTunnel, -1)
    }

    @After
    fun teardown() {
        SshTunnel.disconnect()
    }

    @Test
    fun `connect establishes SSH tunnel and returns local port`() = runBlocking {
        // We will mock SSHClient constructor using mockkConstructor
        mockkConstructor(SSHClient::class)
        val mockConnection = mockk<Connection>(relaxed = true)
        val mockForwarder = mockk<LocalPortForwarder>(relaxed = true)

        every { anyConstructed<SSHClient>().addHostKeyVerifier(any<net.schmizz.sshj.transport.verification.HostKeyVerifier>()) } just Runs
        every { anyConstructed<SSHClient>().connect("example.com", 22) } just Runs
        every { anyConstructed<SSHClient>().authPassword("user", "pass") } just Runs
        every { anyConstructed<SSHClient>().connection } returns mockConnection
        every { anyConstructed<SSHClient>().newLocalPortForwarder(any(), any()) } returns mockForwarder
        every { anyConstructed<SSHClient>().isConnected } returns true

        val port = SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)

        assertTrue(port > 0)
        
        verify(exactly = 1) { anyConstructed<SSHClient>().connect("example.com", 22) }
        verify(exactly = 1) { anyConstructed<SSHClient>().authPassword("user", "pass") }
        verify(exactly = 1) { anyConstructed<SSHClient>().newLocalPortForwarder(any(), any()) }

        // Test reconnect uses cached connection
        val port2 = SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        assertEquals(port, port2)
        verify(exactly = 1) { anyConstructed<SSHClient>().connect("example.com", 22) } // Still 1

        unmockkConstructor(SSHClient::class)
    }
}
