package aeza.hostmaster.mobile.data.repository

import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.remote.ApiService
import java.util.Locale
import javax.inject.Inject

class CheckRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun submitCheck(target: String, type: String) =
        api.submitCheck(
            CheckRequestDto(
                target = target,
                type = type,
                checkType = type.uppercase(Locale.ROOT)
            )
        )

    suspend fun getStatus(jobId: String) = api.getCheckStatus(jobId)
}
