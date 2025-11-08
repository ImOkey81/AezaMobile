package aeza.hostmaster.mobile.domain.usecase

import aeza.hostmaster.mobile.data.repository.CheckRepository
import aeza.hostmaster.mobile.domain.model.CheckType
import javax.inject.Inject

class SubmitCheckUseCase @Inject constructor(
    private val repository: CheckRepository
) {
    suspend operator fun invoke(target: String, type: CheckType) =
        repository.submitCheck(target, type)
}
