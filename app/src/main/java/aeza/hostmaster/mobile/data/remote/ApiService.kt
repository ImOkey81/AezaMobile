package aeza.hostmaster.mobile.data.remote

import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import retrofit2.http.*

interface ApiService {

    @POST("checks")
    suspend fun submitCheck(@Body request: CheckRequestDto): CheckResponseDto

    @GET("checks/{jobId}")
    suspend fun getCheckStatus(@Path("jobId") jobId: String): CheckResponseDto
}
