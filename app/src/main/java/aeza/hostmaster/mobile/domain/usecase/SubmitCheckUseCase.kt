package aeza.hostmaster.mobile.domain.usecase

import aeza.hostmaster.mobile.data.repository.CheckRepository
import javax.inject.Inject

class SubmitCheckUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(target: String, type: String) =
        repository.submitCheck(target, type)
}
