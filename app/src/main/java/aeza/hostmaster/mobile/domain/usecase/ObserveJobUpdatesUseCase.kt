package aeza.hostmaster.mobile.domain.usecase

import aeza.hostmaster.mobile.data.repository.CheckRepository
import javax.inject.Inject

class ObserveJobUpdatesUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    operator fun invoke(jobId: String) = repository.subscribeToJob(jobId)
}
