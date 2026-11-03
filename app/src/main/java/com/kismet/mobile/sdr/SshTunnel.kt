package com.kismet.mobile.sdr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.net.ServerSocket

object SshTunnel {
    private const val TAG = "SshTunnel"
    private var sshClient: SSHClient? = null
    private var localPort: Int = -1

    suspend fun connect(
        host: String,
        sshPort: Int,
        username: String,
        password: String?,
        privateKey: String? = null,
        remoteApiPort: Int = 2501
    ): Int = withContext(Dispatchers.IO) {
        if (sshClient != null && sshClient?.isConnected == true) {
            return@withContext localPort
        }

        Log.d(TAG, "Connecting SSH to $host:$sshPort as $username")
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        try {
            ssh.connect(host, sshPort)
            Log.d(TAG, "SSH TCP connected")
        } catch (e: Exception) {
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
            Log.e(TAG, "SSH auth failed", e)
            throw e
        }

        ssh.connection.keepAlive.keepAliveInterval = 60

        // Find a free local port
        val ss = ServerSocket(0)
        localPort = ss.localPort

        val forwarder = ssh.newLocalPortForwarder(
            Parameters("127.0.0.1", localPort, "127.0.0.1", remoteApiPort),
            ss
        )
        
        Thread({
            try { forwarder.listen() } catch (_: Exception) {}
        }, "kismet-ssh-tunnel").also {
            it.isDaemon = true
            it.start()
        }

        sshClient = ssh
        return@withContext localPort
    }
    
    fun disconnect() {
        val client = sshClient
        sshClient = null
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
