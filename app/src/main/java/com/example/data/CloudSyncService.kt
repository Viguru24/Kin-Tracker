package com.example.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
        fun create(): CloudSyncService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(Interceptor { chain ->
                    if (AppConfig.isTestEnvironment) {
                        val req = chain.request()
                        val dummyJson = if (req.method == "GET") {
                            "{\"members\":{},\"lastUpdated\":${System.currentTimeMillis()}}"
                        } else {
                            "{\"success\":true}"
                        }
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK (Test Isolation)")
                            .body(dummyJson.toResponseBody("application/json".toMediaTypeOrNull()))
                            .build()
                    } else {
                        chain.proceed(chain.request())
                    }
                })
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(AppConfig.BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(CloudSyncService::class.java)
        }
    }
}
