package aeza.hostmaster.mobile.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aeza.hostmaster.mobile.data.mapper.CheckMapper
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import aeza.hostmaster.mobile.domain.model.isTerminal
import aeza.hostmaster.mobile.domain.usecase.GetCheckResultUseCase
import aeza.hostmaster.mobile.domain.usecase.SubmitCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HISTORY_LIMIT = 20
private const val POLL_DELAY_MILLIS = 1000L
private const val POLL_ATTEMPTS = 60

data class CheckUiState(
    val checkType: CheckType? = null,
    val isLoading: Boolean = false,
    val activeJobId: String? = null,
    val history: List<CheckResult> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class CheckViewModel @Inject constructor(
    private val submitCheck: SubmitCheckUseCase,
    private val getCheckResult: GetCheckResultUseCase,
    private val mapper: CheckMapper
) : ViewModel() {

    private val _state = MutableStateFlow(CheckUiState())
    val state: StateFlow<CheckUiState> = _state

    fun initialize(type: CheckType) {
        if (_state.value.checkType == null) {
            _state.update { it.copy(checkType = type) }
        }
    }

    fun submit(target: String) {
        val type = _state.value.checkType ?: return
        val sanitizedTarget = sanitizeTarget(target, type)?.takeIf { it.isNotBlank() } ?: run {
            _state.update { it.copy(errorMessage = validationErrorMessage(type)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                val submitResponse = submitCheck(sanitizedTarget, type)
                val pendingResult = mapper.toDomain(submitResponse, type)
                updateHistory(pendingResult)
                val finalResult = waitForCompletion(pendingResult.jobId, type)
                updateHistory(finalResult)
                _state.update {
                    it.copy(
                        isLoading = false,
                        activeJobId = finalResult.jobId,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                val error = throwable.message ?: "Не удалось выполнить проверку"
                _state.update { current ->
                    val updatedHistory = current.activeJobId
                        ?.let { jobId -> current.history.markAsFailed(jobId, error) }
                        ?: current.history

                    current.copy(
                        isLoading = false,
                        activeJobId = null,
                        history = updatedHistory,
                        errorMessage = error
                    )
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun sanitizeTarget(rawInput: String, type: CheckType): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        return when (type) {
            is CheckType.Http -> sanitizeHttpTarget(trimmed)
            is CheckType.Tcp -> sanitizeTcpTarget(trimmed)
            is CheckType.Ping,
            is CheckType.Dns,
            is CheckType.Info -> sanitizeHostLikeTarget(trimmed)
        }
    }

    private fun validationErrorMessage(type: CheckType): String = when (type) {
        is CheckType.Http -> "Введите корректный URL вида https://example.com"
        is CheckType.Tcp -> "Введите адрес и порт в формате host:port"
        is CheckType.Ping,
        is CheckType.Dns,
        is CheckType.Info -> "Введите домен или IP-адрес"
    }

    private fun sanitizeHostLikeTarget(value: String): String? {
        val uri = Uri.parse(value)
        val hostFromUri = uri.host?.takeIf { it.isNotBlank() }
        val manual = value.substringAfter("://", value)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val candidate = (hostFromUri ?: manual).trim()
        val withoutFragments = candidate
            .substringBefore('#')
            .substringBefore('?')
        val host = when {
            hostFromUri != null -> withoutFragments
            withoutFragments.count { it == ':' } > 1 -> withoutFragments // IPv6 address
            else -> withoutFragments.substringBefore(':')
        }.trim().trimEnd('.')
        return host.takeIf { it.isNotEmpty() }
    }

    private fun sanitizeHttpTarget(value: String): String? {
        val candidate = if ("://" in value) value else "https://$value"
        val uri = Uri.parse(candidate)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host
        if (host.isNullOrBlank() || scheme == null || scheme !in listOf("http", "https")) {
            return null
        }
        val builder = uri.buildUpon()
        if (uri.path.isNullOrEmpty()) {
            builder.path("/")
        }
        return builder.build().toString()
    }

    private fun sanitizeTcpTarget(value: String): String? {
        val withoutScheme = value.substringAfter("://", value)
        val hostPort = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val delimiterIndex = hostPort.lastIndexOf(':')
        if (delimiterIndex <= 0 || delimiterIndex == hostPort.length - 1) return null
        val hostPart = hostPort.substring(0, delimiterIndex)
        val portPart = hostPort.substring(delimiterIndex + 1)
        val host = sanitizeHostLikeTarget(hostPart) ?: return null
        val port = portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return "$host:$port"
    }

    private suspend fun waitForCompletion(jobId: String, type: CheckType): CheckResult {
        repeat(POLL_ATTEMPTS) { attempt ->
            val resultResponse = getCheckResult(jobId)
            val result = mapper.toDomain(resultResponse, type)
            if (result.isTerminal() || attempt == POLL_ATTEMPTS - 1) {
                return result
            }
            delay(POLL_DELAY_MILLIS)
        }
        // Should not reach here because of return inside loop, but keep compiler happy
        val fallbackResponse = getCheckResult(jobId)
        return mapper.toDomain(fallbackResponse, type)
    }

    private fun updateHistory(result: CheckResult) {
        _state.update { current ->
            val updatedHistory = current.history.prependOrReplace(result).take(HISTORY_LIMIT)
            current.copy(
                history = updatedHistory,
                activeJobId = result.jobId
            )
        }
    }

    private fun List<CheckResult>.prependOrReplace(result: CheckResult): List<CheckResult> {
        val mutable = toMutableList()
        val existingIndex = indexOfFirst { it.jobId == result.jobId }
        if (existingIndex >= 0) {
            mutable[existingIndex] = result
            if (existingIndex != 0) {
                mutable.removeAt(existingIndex)
                mutable.add(0, result)
            }
        } else {
            mutable.add(0, result)
        }
        return mutable
    }

    private fun List<CheckResult>.markAsFailed(jobId: String, message: String): List<CheckResult> {
        val mutable = toMutableList()
        val index = indexOfFirst { it.jobId == jobId }
        if (index >= 0) {
            val failed = mutable[index].copy(status = "failed", details = message)
            mutable[index] = failed
            if (index != 0) {
                mutable.removeAt(index)
                mutable.add(0, failed)
            }
        }
        return mutable
    }
}
