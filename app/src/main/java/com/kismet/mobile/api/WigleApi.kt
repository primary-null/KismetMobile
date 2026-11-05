package com.kismet.mobile.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import com.google.gson.annotations.SerializedName

interface WigleApi {
    @Multipart
    @POST("api/v2/file/upload")
    suspend fun upload(@Part file: MultipartBody.Part): Response<WigleUploadResult>
}

data class WigleUploadResult(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String = "",
)

object WigleClient {
    fun build(apiName: String, apiToken: String): WigleApi {
        val cred = android.util.Base64.encodeToString(
            "$apiName:$apiToken".toByteArray(), android.util.Base64.NO_WRAP
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Basic $cred")
                        .build()
                )
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://api.wigle.net/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WigleApi::class.java)
    }
}
