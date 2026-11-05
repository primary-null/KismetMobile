package com.kismet.mobile.repository

import com.google.gson.Gson
import com.kismet.mobile.api.KismetApi
import com.kismet.mobile.model.ChannelData
import com.kismet.mobile.model.DataSource
import com.kismet.mobile.model.Device
import com.kismet.mobile.model.PhyStat
import com.kismet.mobile.model.SystemStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.OutputStream

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

class KismetRepository(private val api: KismetApi) {

    suspend fun checkLogin(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.checkLogin()
            if (response.isSuccessful) Result.Success(Unit)
            else Result.Error("Login failed: ${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun getSystemStatus(): Result<SystemStatus> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSystemStatus()
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("Status unavailable: ${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch status")
        }
    }

    suspend fun getAllDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllDevices()
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("Devices unavailable: ${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch devices")
        }
    }

    suspend fun getDevicesSince(timestamp: Long): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDevicesSince(timestamp)
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch devices")
        }
    }

    suspend fun getAllPhys(): Result<List<PhyStat>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllPhys()
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch phys")
        }
    }

    suspend fun getAllSources(): Result<List<DataSource>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAllSources()
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch sources")
        }
    }

    suspend fun getChannels(): Result<ChannelData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getChannels()
            if (response.isSuccessful && response.body() != null)
                Result.Success(response.body()!!)
            else Result.Error("${response.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed to fetch channels")
        }
    }

    suspend fun setSourceHop(uuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = """{"hop":true}"""
            val r = api.setSourceChannel(uuid, json)
            if (r.isSuccessful) Result.Success(Unit) else Result.Error("${r.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed")
        }
    }

    suspend fun pauseSource(uuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = api.pauseSource(uuid)
            if (r.isSuccessful) Result.Success(Unit) else Result.Error("${r.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed")
        }
    }

    suspend fun resumeSource(uuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = api.resumeSource(uuid)
            if (r.isSuccessful) Result.Success(Unit) else Result.Error("${r.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed")
        }
    }

    suspend fun addSource(definition: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = api.addSource(Gson().toJson(mapOf("definition" to definition)))
            if (r.isSuccessful) Result.Success(Unit) else Result.Error("${r.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed")
        }
    }

    suspend fun streamPcap(
        outputStream: OutputStream,
        uuid: String? = null,
        deviceKey: String? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val response = when {
                deviceKey != null -> api.streamDevicePcap(deviceKey)
                uuid != null -> api.streamSourcePcap(uuid)
                else -> api.streamAllPcap()
            }
            if (!response.isSuccessful || response.body() == null)
                return@withContext Result.Error("${response.code()}")
            val body = response.body()!!
            val captureContext = currentCoroutineContext()
            val bytes = suspendCancellableCoroutine<Long> { continuation ->
                // Cancellation may arrive while read() blocks, before the next loop check.
                continuation.invokeOnCancellation { runCatching { body.close() } }
                try {
                    var copied = 0L
                    val buf = ByteArray(8192)
                    val limit = 50L * 1024 * 1024
                    body.use {
                        val input = it.byteStream()
                        while (copied < limit) {
                            captureContext.ensureActive()
                            val read = input.read(buf, 0, minOf(buf.size.toLong(), limit - copied).toInt())
                            if (read == -1) break
                            outputStream.write(buf, 0, read)
                            copied += read
                        }
                    }
                    continuation.resume(copied)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
            Result.Success(bytes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Error(e.message ?: "Failed")
        }
    }
}
