package com.kismet.mobile.api

import com.kismet.mobile.model.ChannelData
import com.kismet.mobile.model.DataSource
import com.kismet.mobile.model.Device
import com.kismet.mobile.model.PhyStat
import com.kismet.mobile.model.SystemStatus
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface KismetApi {

    @GET("system/status.json")
    suspend fun getSystemStatus(): Response<SystemStatus>

    @POST("devices/views/all/devices.json")
    suspend fun getAllDevices(): Response<List<Device>>

    @POST("devices/views/all/last-time/{timestamp}/devices.json")
    suspend fun getDevicesSince(
        @Path("timestamp") timestamp: Long
    ): Response<List<Device>>

    @GET("channels/channels.json")
    suspend fun getChannels(): Response<ChannelData>

    @POST("phy/all_phys.json")
    suspend fun getAllPhys(): Response<List<PhyStat>>

    @GET("datasource/all_sources.json")
    suspend fun getAllSources(): Response<List<DataSource>>

    @GET("session/check_login")
    suspend fun checkLogin(): Response<Unit>

    @GET("session/check_session")
    suspend fun checkSession(): Response<Unit>

    // --- Datasource control ---

    @FormUrlEncoded
    @POST("datasource/by-uuid/{uuid}/set_channel")
    suspend fun setSourceChannel(
        @Path("uuid") uuid: String,
        @Field("json") json: String,
    ): Response<Unit>

    @FormUrlEncoded
    @POST("datasource/by-uuid/{uuid}/resume_source.cmd")
    suspend fun resumeSource(
        @Path("uuid") uuid: String,
        @Field("json") json: String = "{}",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("datasource/by-uuid/{uuid}/pause_source.cmd")
    suspend fun pauseSource(
        @Path("uuid") uuid: String,
        @Field("json") json: String = "{}",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("datasource/add_source.cmd")
    suspend fun addSource(
        @Field("json") json: String,
    ): Response<Unit>

    // --- PCAP streaming ---

    @Streaming
    @GET("pcap/all_packets.pcapng")
    suspend fun downloadPcap(): Response<ResponseBody>

    @Streaming
    @GET("pcap/all_packets.pcapng")
    suspend fun streamAllPcap(): Response<ResponseBody>

    @Streaming
    @GET("datasource/pcap/by-uuid/{uuid}/packets.pcapng")
    suspend fun streamSourcePcap(@Path("uuid") uuid: String): Response<ResponseBody>

    @Streaming
    @GET("devices/by-key/{key}/pcap/packets.pcapng")
    suspend fun streamDevicePcap(@Path("key") key: String): Response<ResponseBody>
}
