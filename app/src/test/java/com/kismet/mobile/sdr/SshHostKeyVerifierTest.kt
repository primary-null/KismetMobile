package com.kismet.mobile.sdr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator

class SshHostKeyVerifierTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `first key persists across verifiers and changed key is rejected`() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val original = generator.generateKeyPair().public
        val changed = generator.generateKeyPair().public
        val directory = temporaryFolder.newFolder()
        assertTrue(SshHostKeyVerifier(directory).verify("example.com", 22, original))
        assertTrue(SshHostKeyVerifier(directory).verify("example.com", 22, original))
        assertFalse(SshHostKeyVerifier(directory).verify("example.com", 22, changed))
        assertTrue(SshHostKeyVerifier(directory).verify("example.com", 2222, changed))
    }

    @Test fun `unwritable trust storage fails closed`() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
        assertFalse(SshHostKeyVerifier(temporaryFolder.newFile()).verify("example.com", 22, key))
    }
}
