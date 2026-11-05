package com.kismet.mobile.sdr

import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

class SshTunnelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val releaseForwarder = CountDownLatch(1)
    private val sockets = mutableListOf<ServerSocket>()

    @Before fun setup() {
        SshTunnel.disconnect()
        mockkConstructor(SSHClient::class)
        val connection = mockk<Connection>(relaxed = true)
        val forwarder = mockk<LocalPortForwarder>()
        every { anyConstructed<SSHClient>().addHostKeyVerifier(any<net.schmizz.sshj.transport.verification.HostKeyVerifier>()) } just Runs
        every { anyConstructed<SSHClient>().connect("example.com", 22) } just Runs
        every { anyConstructed<SSHClient>().authPassword("user", "pass") } just Runs
        every { anyConstructed<SSHClient>().connection } returns connection
        every { anyConstructed<SSHClient>().newLocalPortForwarder(any(), any()) } answers {
            sockets.add(secondArg())
            forwarder
        }
        every { anyConstructed<SSHClient>().isConnected } returns true
        every { forwarder.listen() } answers { releaseForwarder.await(); Unit }
    }

    @After fun teardown() {
        val thread = forwarderThread()
        releaseForwarder.countDown()
        SshTunnel.disconnect()
        thread?.join(2_000)
        unmockkConstructor(SSHClient::class)
    }

    private fun forwarderThread(): Thread? {
        val field = SshTunnel::class.java.getDeclaredField("forwarderThread").apply { isAccessible = true }
        return field.get(SshTunnel) as Thread?
    }

    @Test fun `healthy tunnel is reused and listener binds only to loopback`() = runBlocking {
        val port = SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        assertTrue(port > 0)
        assertTrue(sockets.single().inetAddress.isLoopbackAddress)
        assertEquals(port, SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root))
        verify(exactly = 1) { anyConstructed<SSHClient>().connect("example.com", 22) }
    }

    @Test fun `dead accept loop is replaced even while SSH reports connected`() = runBlocking {
        SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        val thread = forwarderThread()!!
        releaseForwarder.countDown()
        thread.join(2_000)
        assertFalse(thread.isAlive)
        assertTrue(sockets.first().isClosed)
        SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        verify(exactly = 2) { anyConstructed<SSHClient>().connect("example.com", 22) }
    }

    @Test fun `closed listener is replaced even if accept thread is still alive`() = runBlocking {
        SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        sockets.first().close()
        assertTrue(forwarderThread()!!.isAlive)
        SshTunnel.connect("example.com", 22, "user", "pass", trustDirectory = temporaryFolder.root)
        verify(exactly = 2) { anyConstructed<SSHClient>().connect("example.com", 22) }
    }
}
