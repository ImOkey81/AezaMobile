package aeza.hostmaster.mobile.data.remote

import aeza.hostmaster.mobile.data.model.CheckHostResultDto
import aeza.hostmaster.mobile.data.model.CheckHostStartResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("check-ping")
    suspend fun startPing(@Query("host") host: String): CheckHostStartResponseDto

    @GET("check-http")
    suspend fun startHttp(@Query("host") host: String): CheckHostStartResponseDto

    @GET("check-tcp")
    suspend fun startTcp(@Query("host") host: String): CheckHostStartResponseDto

    @GET("check-dns")
    suspend fun startDns(@Query("host") host: String): CheckHostStartResponseDto

    @GET("check-result/{requestId}")
    suspend fun fetchResult(@Path("requestId") requestId: String): CheckHostResultDto
}
