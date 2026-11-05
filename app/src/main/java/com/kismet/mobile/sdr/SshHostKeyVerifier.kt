package com.kismet.mobile.sdr

import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.common.KeyType
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey

/** Trust on first use: retain the first key for each endpoint and reject changes. */
internal class SshHostKeyVerifier(private val directory: File) : HostKeyVerifier {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean = synchronized(lock) {
        val pin = pinFile(hostname, port)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        try {
            val accepted = if (pin.exists()) {
                MessageDigest.isEqual(pin.readBytes(), fingerprint)
            } else {
                if (!directory.isDirectory && !directory.mkdirs()) return@synchronized false
                pin.writeBytes(fingerprint)
                true
            }
            if (accepted) {
                // A sidecar also upgrades existing 32-byte pins after a successful match.
                val algorithm = File(directory, "${pin.name}.algorithm")
                val value = KeyType.fromKey(key).toString()
                if (!algorithm.exists() || algorithm.readText() != value) algorithm.writeText(value)
            }
            accepted
        } catch (_: java.io.IOException) {
            false
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = synchronized(lock) {
        val pin = pinFile(hostname, port)
        val algorithm = File(directory, "${pin.name}.algorithm")
        try {
            if (!pin.isFile || pin.length() != 32L || !algorithm.isFile) return@synchronized emptyList()
            val value = algorithm.readText().trim()
            if (KeyType.fromString(value) == KeyType.UNKNOWN) emptyList() else listOf(value)
        } catch (_: java.io.IOException) {
            emptyList()
        }
    }

    private fun pinFile(hostname: String, port: Int): File {
        val endpoint = "$hostname:$port".toByteArray(Charsets.UTF_8)
        val name = MessageDigest.getInstance("SHA-256").digest(endpoint)
            .joinToString("") { "%02x".format(it) }
        return File(directory, name)
    }

    private companion object {
        val lock = Any()
    }
}
