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

interface KinTrackerApiService {

    // 1. Create a new circle with 6-char code
    @POST("api/circles/create")
    @Headers("Content-Type: application/json")
    suspend fun createCircle(@Body request: CreateCircleRequest): Response<CircleMutationResponse>

    // 2. Join circle with 6-char code
    @POST("api/circles/join")
    @Headers("Content-Type: application/json")
    suspend fun joinCircle(@Body request: JoinCircleRequest): Response<CircleMutationResponse>

    // 3. Sync circle members and state
    @GET("api/circles/{circleId}/sync")
    suspend fun syncCircle(@Path("circleId") circleId: String): Response<CircleSyncResponse>

    // 4. Atomic Location Update for this device
    @POST("api/circles/{circleId}/location")
    @Headers("Content-Type: application/json")
    suspend fun updateLocation(
        @Path("circleId") circleId: String,
        @Body locationUpdate: CircleLocationUpdateRequest
    ): Response<ResponseBody>

    // 5. Regenerate Circle Invite Code
    @POST("api/circles/{circleId}/regenerate-code")
    suspend fun regenerateCode(@Path("circleId") circleId: String): Response<CircleMutationResponse>

    // 6. Leave / Remove member from circle
    @POST("api/circles/{circleId}/leave")
    @Headers("Content-Type: application/json")
    suspend fun leaveCircle(
        @Path("circleId") circleId: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    // 7. Shopping List Sync
    @POST("api/circles/{circleId}/shopping")
    @Headers("Content-Type: application/json")
    suspend fun syncShoppingItem(
        @Path("circleId") circleId: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    companion object {
        fun create(): KinTrackerApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(AppConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(KinTrackerApiService::class.java)
        }
    }
}
