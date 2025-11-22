package aeza.hostmaster.mobile.domain.usecase

import aeza.hostmaster.mobile.data.repository.CheckRepository
import javax.inject.Inject

class GetCheckStatusUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(checkId: String) =
        repository.getStatus(checkId)
}
