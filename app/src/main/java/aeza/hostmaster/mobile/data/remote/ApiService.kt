package aeza.hostmaster.mobile.data.remote

import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/checks")
    suspend fun createCheck(@Body request: CheckRequestDto): CheckResponseDto

    @GET("api/checks/{jobId}")
    suspend fun getCheckStatus(@Path("jobId") jobId: String): CheckResponseDto
}
