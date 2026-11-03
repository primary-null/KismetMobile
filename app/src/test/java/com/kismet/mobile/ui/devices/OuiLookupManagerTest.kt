package com.kismet.mobile.ui.devices

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class OuiLookupManagerTest {

    @org.junit.Before
    fun setup() {
        val isLoadedField = OuiLookupManager::class.java.getDeclaredField("isLoaded")
        isLoadedField.isAccessible = true
        isLoadedField.set(OuiLookupManager, false)

        val ouiMapField = OuiLookupManager::class.java.getDeclaredField("ouiMap")
        ouiMapField.isAccessible = true
        (ouiMapField.get(OuiLookupManager) as MutableMap<*, *>).clear()
    }

    @Test
    fun `resolveMac correctly parses JSON and resolves MAC prefixes`() = runBlocking {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } answers { 
            println(it.invocation.args[2])
            0 
        }

        val mockContext = mockk<Context>()
        val mockAssetManager = mockk<AssetManager>()
        
        val jsonString = """
        [
            {
                "prefix": "00:11:22",
                "organization": {
                    "name": "Test Company Inc."
                }
            },
            {
                "prefix": "AA-BB-CC",
                "organization": {
                    "name": "Another Corp"
                }
            }
        ]
        """.trimIndent()

        every { mockContext.assets } returns mockAssetManager
        every { mockAssetManager.open("oui.json") } returns ByteArrayInputStream(jsonString.toByteArray())

        // Load the database
        OuiLookupManager.init(mockContext)

        // Test standard MAC resolution
        val result1 = OuiLookupManager.resolveMac("00:11:22:33:44:55")
        assertEquals("Test Company Inc.", result1)

        // Test alternate format MAC resolution
        val result2 = OuiLookupManager.resolveMac("AA-BB-CC-DD-EE-FF")
        assertEquals("Another Corp", result2)
        
        // Test lower-case resolution
        val result3 = OuiLookupManager.resolveMac("aa:bb:cc:11:22:33")
        assertEquals("Another Corp", result3)

        // Test unknown MAC
        val unknown = OuiLookupManager.resolveMac("FF:FF:FF:FF:FF:FF")
        assertNull(unknown)
    }
}
