package com.example.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface CloudSyncService {

    @GET("{token}")
    suspend fun getGroupData(@Path(value = "token", encoded = true) token: String): Response<ResponseBody>

    @PUT("{token}")
    @Headers("Content-Type: application/json")
    suspend fun updateGroupData(
        @Path(value = "token", encoded = true) token: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("new")
    @Headers("Content-Type: application/text")
    suspend fun createNewGroup(@Body body: RequestBody): Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://api.cosmowhisper.com/sync/"

        fun create(): CloudSyncService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(CloudSyncService::class.java)
        }
    }
}
