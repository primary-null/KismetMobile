package com.kismet.mobile.sdr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

object SshTunnel {
    private const val TAG = "SshTunnel"
    private var sshClient: SSHClient? = null
    private var localPort: Int = -1
    private var serverSocket: ServerSocket? = null
    private var target: List<String>? = null
    @Volatile private var forwarderThread: Thread? = null

    suspend fun connect(
        host: String,
        sshPort: Int,
        username: String,
        password: String?,
        privateKey: String? = null,
        remoteApiPort: Int = 2501,
        trustDirectory: File,
    ): Int = withContext(Dispatchers.IO) {
        val requestedTarget = listOf(host, sshPort.toString(), username, remoteApiPort.toString())
        if (sshClient?.isConnected == true && target == requestedTarget &&
            serverSocket?.isClosed == false && forwarderThread?.isAlive == true) {
            return@withContext localPort
        }
        disconnect()

        val ssh = SSHClient()
        ssh.addHostKeyVerifier(SshHostKeyVerifier(trustDirectory))
        try {
            ssh.connect(host, sshPort)
            Log.d(TAG, "SSH TCP connected")
        } catch (e: Exception) {
            runCatching { ssh.close() }
            Log.e(TAG, "SSH connect failed", e)
            throw e
        }
        try {
            if (privateKey != null && privateKey.isNotBlank()) {
                // Write PEM to temp file; use single-arg loadKeys() which auto-detects
                // key format via KeyProviderUtil (handles OpenSSH v1, PKCS8, PuTTY, etc.).
                // The 3-arg overload loadKeys(String,String,PasswordFinder) bypasses
                // format detection and hardcodes PKCS8KeyFile — do NOT use it here.
                val tempFile = java.io.File.createTempFile("kismet_ssh_", null)
                try {
                    tempFile.writeText(privateKey)
                    val keyProvider = ssh.loadKeys(tempFile.absolutePath)
                    ssh.authPublickey(username, keyProvider)
                    Log.d(TAG, "SSH pubkey auth ok")
                } finally {
                    tempFile.delete()
                }
            } else if (password != null) {
                ssh.authPassword(username, password)
                Log.d(TAG, "SSH password auth ok")
            }
        } catch (e: Exception) {
            runCatching { ssh.close() }
            Log.e(TAG, "SSH auth failed", e)
            throw e
        }

        var socket: ServerSocket? = null
        try {
            ssh.connection.keepAlive.keepAliveInterval = 60

            // Find a free local port
            val ss = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
            socket = ss
            serverSocket = ss
            localPort = ss.localPort

            val forwarder = ssh.newLocalPortForwarder(
                Parameters("127.0.0.1", localPort, "127.0.0.1", remoteApiPort),
                ss
            )

            Thread({
                try {
                    forwarder.listen()
                } catch (e: Exception) {
                    if (!ss.isClosed) Log.w(TAG, "SSH forwarding stopped", e)
                } finally {
                    runCatching { ss.close() }
                }
            }, "kismet-ssh-tunnel").also {
                it.isDaemon = true
                forwarderThread = it
                it.start()
            }

            sshClient = ssh
            target = requestedTarget
        } catch (e: Exception) {
            runCatching { socket?.close() }
            runCatching { ssh.close() }
            serverSocket = null
            forwarderThread = null
            localPort = -1
            throw e
        }
        return@withContext localPort
    }

    fun disconnect() {
        val client = sshClient
        sshClient = null
        val socket = serverSocket
        serverSocket = null
        localPort = -1
        target = null
        forwarderThread = null
        try { socket?.close() } catch (_: Exception) { }
        if (client != null) {
            Thread({
                try {
                    client.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Disconnect error", e)
                }
            }, "kismet-ssh-disconnect").start()
        }
    }
}
