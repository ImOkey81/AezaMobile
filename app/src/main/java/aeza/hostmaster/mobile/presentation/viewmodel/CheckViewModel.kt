package aeza.hostmaster.mobile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aeza.hostmaster.mobile.domain.usecase.GetCheckStatusUseCase
import aeza.hostmaster.mobile.domain.usecase.SubmitCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckUiState(
    val isLoading: Boolean = false,
    val jobId: String? = null,
    val result: String? = null,
    val error: String? = null
)

@HiltViewModel
class CheckViewModel @Inject constructor(
    private val submitCheck: SubmitCheckUseCase,
    private val getCheckStatus: GetCheckStatusUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CheckUiState())
    val state: StateFlow<CheckUiState> = _state

    fun submit(target: String, type: String) {
        viewModelScope.launch {
            try {
                _state.value = CheckUiState(isLoading = true)
                val response = submitCheck(target, type)
                val jobId = response.jobId
                _state.value = CheckUiState(jobId = jobId)
                getStatus(jobId)
            } catch (e: Exception) {
                _state.value = CheckUiState(error = e.message)
            }
        }
    }

    private fun getStatus(jobId: String) {
        viewModelScope.launch {
            try {
                val response = getCheckStatus(jobId)
                _state.value = CheckUiState(
                    jobId = jobId,
                    result = response.results.toString(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = CheckUiState(error = e.message)
            }
        }
    }
}
