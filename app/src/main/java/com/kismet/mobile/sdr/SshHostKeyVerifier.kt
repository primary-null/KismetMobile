package com.kismet.mobile.sdr

import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey

/** Trust on first use: retain the first key for each endpoint and reject changes. */
internal class SshHostKeyVerifier(private val directory: File) : HostKeyVerifier {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = synchronized(lock) {
        val endpoint = "$hostname:$port".toByteArray(Charsets.UTF_8)
        val name = MessageDigest.getInstance("SHA-256").digest(endpoint)
            .joinToString("") { "%02x".format(it) }
        val pin = File(directory, name)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        try {
            if (pin.exists()) {
                MessageDigest.isEqual(pin.readBytes(), fingerprint)
            } else {
                if (!directory.isDirectory && !directory.mkdirs()) return@synchronized false
                pin.writeBytes(fingerprint)
                true
            }
        } catch (_: java.io.IOException) {
            false
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()

    private companion object {
        val lock = Any()
    }
}
