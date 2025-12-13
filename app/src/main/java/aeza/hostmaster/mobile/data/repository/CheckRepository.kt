package aeza.hostmaster.mobile.data.repository

import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.data.remote.ApiService
import aeza.hostmaster.mobile.domain.model.CheckType
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

class CheckRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun submitCheck(target: String, type: CheckType): CheckResponseDto = executeWithErrorHandling {
        val request = CheckRequestDto(
            target = target,
            checkTypes = listOf(type.backendName.uppercase(Locale.ROOT))
        )

        val response = api.createCheck(request)
        validateJobResponse(response)

        response.withDefaults(target, type)
    }

    suspend fun getResult(jobId: String): CheckResponseDto = executeWithErrorHandling {
        val response = api.getCheckStatus(jobId)
        validateJobResponse(response)
        response
    }

    private suspend fun <T> executeWithErrorHandling(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: Throwable) {
            throw mapError(error)
        }
    }

    private fun mapError(error: Throwable): Throwable {
        if (error is CancellationException) throw error
        return when (error) {
            is HttpException -> {
                val status = error.code()
                val message = parseErrorMessage(error.response()?.errorBody())
                    ?: when (status) {
                        503 -> "Сервис временно недоступен. Попробуйте позже."
                        500 -> "Внутренняя ошибка сервера. Попробуйте позже."
                        in 400..499 -> "Некорректные параметры запроса. Проверьте введённые данные."
                        else -> "Не удалось выполнить запрос. Код ошибки: $status"
                    }
                Exception(message, error)
            }

            is IOException -> Exception(
                "Не удалось подключиться к серверу. Проверьте интернет-соединение и повторите попытку.",
                error
            )

            else -> Exception(error.message ?: "Не удалось выполнить запрос", error)
        }
    }

    private fun validateJobResponse(response: CheckResponseDto) {
        if (response.jobId.isNullOrBlank()) {
            val message = response.status.takeIf { it.isNotBlank() }
                ?: "Не удалось запустить проверку. Попробуйте позже."
            throw Exception(message)
        }
    }

    private fun CheckResponseDto.withDefaults(target: String, type: CheckType): CheckResponseDto =
        this.copy(
            status = this.status.ifBlank { "pending" },
            type = this.type ?: type.backendName,
            target = this.target ?: target,
        )

    private fun parseErrorMessage(body: ResponseBody?): String? {
        val raw = runCatching { body?.string() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val json = JSONObject(raw)
            when {
                json.has("errors") -> {
                    val fieldErrors = json.optJSONObject("errors")
                    if (fieldErrors != null) {
                        val keys = fieldErrors.keys()
                        while (keys.hasNext()) {
                            val message = fieldErrors.optString(keys.next())
                            if (message.isNotBlank()) {
                                return message
                            }
                        }
                    }
                    json.optString("message").takeIf { it.isNotBlank() }
                }

                json.has("message") -> json.optString("message").takeIf { it.isNotBlank() }
                json.has("reason") -> json.optString("reason").takeIf { it.isNotBlank() }
                json.has("error") -> json.optString("error").takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

}
