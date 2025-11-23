package aeza.hostmaster.mobile.domain.usecase

import aeza.hostmaster.mobile.data.repository.CheckRepository
import javax.inject.Inject

class GetCheckResultUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(jobId: String) =
        repository.getResult(jobId)
}
