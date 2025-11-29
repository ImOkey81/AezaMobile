package aeza.hostmaster.mobile.data.remote

import aeza.hostmaster.mobile.data.model.CheckHostResultDto
import aeza.hostmaster.mobile.data.model.CheckHostStartResponseDto
import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @Headers("Accept: application/json")
    @GET("check-ping")
    suspend fun startPing(@Query("host") host: String): CheckHostStartResponseDto

    @Headers("Accept: application/json")
    @GET("check-http")
    suspend fun startHttp(@Query("host") host: String): CheckHostStartResponseDto

    @Headers("Accept: application/json")
    @GET("check-tcp")
    suspend fun startTcp(@Query("host") host: String): CheckHostStartResponseDto

    @Headers("Accept: application/json")
    @GET("check-dns")
    suspend fun startDns(@Query("host") host: String): CheckHostStartResponseDto

    @Headers("Accept: application/json")
    @GET("check-result-extended/{requestId}")
    suspend fun fetchResult(@Path("requestId") requestId: String): CheckHostResultDto

    @Headers("Accept: application/json")
    @GET("check-result/{requestId}")
    suspend fun fetchLegacyResult(@Path("requestId") requestId: String): JsonElement
}
