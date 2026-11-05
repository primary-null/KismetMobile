package com.kismet.mobile.repository

import com.google.gson.JsonParser
import com.kismet.mobile.api.KismetApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.OutputStream

class KismetRepositoryTest {
    @Test fun `source definition is valid JSON with quotes and backslashes`() = runBlocking {
        val api = mockk<KismetApi>()
        val body = slot<String>()
        coEvery { api.addSource(capture(body)) } returns Response.success(Unit)
        val definition = "wlan0:name=\"Survey\\capture\"\nline"
        assertTrue(KismetRepository(api).addSource(definition) is Result.Success)
        assertEquals(definition, JsonParser.parseString(body.captured).asJsonObject["definition"].asString)
    }

    @Test fun `capture stops at exactly fifty MiB`() = runBlocking {
        val api = mockk<KismetApi>()
        val limit = 50 * 1024 * 1024
        coEvery { api.streamAllPcap() } returns Response.success(ByteArray(limit + 23).toResponseBody())
        var written = 0L
        val output = object : OutputStream() {
            override fun write(value: Int) { written++ }
            override fun write(buffer: ByteArray, offset: Int, length: Int) { written += length }
        }
        assertEquals(Result.Success(limit.toLong()), KismetRepository(api).streamPcap(output))
        assertEquals(limit.toLong(), written)
        coVerify(exactly = 1) { api.streamAllPcap() }
    }

    @Test(expected = CancellationException::class)
    fun `repository propagates coroutine cancellation`() = runBlocking {
        val api = mockk<KismetApi>()
        coEvery { api.getAllDevices() } throws CancellationException("cancelled")
        KismetRepository(api).getAllDevices()
        Unit
    }
}
