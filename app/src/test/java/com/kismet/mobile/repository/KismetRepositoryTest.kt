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
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody
import okio.Source
import okio.Buffer
import okio.Timeout
import okio.buffer

class KismetRepositoryTest {
    @Test fun `cancelling a blocked capture closes its body without waiting for a read timeout`() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                entered.countDown()
                check(released.await(5, TimeUnit.SECONDS))
                throw java.io.IOException("closed")
            }
            override fun close() { closed.set(true); released.countDown() }
            override fun timeout() = Timeout.NONE
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = source
        }
        val api = mockk<KismetApi>()
        coEvery { api.streamAllPcap() } returns Response.success(body)
        val capture = launch(Dispatchers.IO) { KismetRepository(api).streamPcap(ByteArrayOutputStream()) }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            capture.cancel()
            withTimeout(2_000) { capture.join() }
            assertTrue(closed.get())
        } finally {
            released.countDown()
            capture.cancel()
        }
    }

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
