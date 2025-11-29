package aeza.hostmaster.mobile.data.repository

import aeza.hostmaster.mobile.data.model.CheckHostResultDto
import aeza.hostmaster.mobile.data.model.CheckHostStartResponseDto
import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.data.remote.ApiService
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.JsonElement
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

private const val DEFAULT_MAX_NODES = 5

class CheckRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun submitCheck(target: String, type: CheckType): CheckResponseDto = executeWithErrorHandling {
        val startResponse = when (type) {
            is CheckType.Ping -> api.startPing(target, DEFAULT_MAX_NODES, null)
            is CheckType.Http -> api.startHttp(target, DEFAULT_MAX_NODES, null)
            is CheckType.Tcp -> api.startTcp(target, DEFAULT_MAX_NODES, null)
            is CheckType.Dns -> api.startDns(target, DEFAULT_MAX_NODES, null)
            is CheckType.Info -> throw IllegalArgumentException("Info check is not supported by Check-Host API")
        }

        validateStartResponse(startResponse)

        CheckResponseDto(
            jobId = startResponse.requestId ?: "",
            status = startResponse.statusLabel(),
            type = type.backendName,
            target = target,
            payload = null,
            results = null,
            createdAt = null,
            updatedAt = null,
            context = null
        )
    }

    suspend fun getResult(jobId: String): CheckResponseDto = executeWithErrorHandling {
        val extendedResult = api.fetchResult(jobId)
        val completedResult = if (extendedResult.results.isNullMissingOrOnlyNullNodes()) {
            val legacyResults = api.fetchLegacyResult(jobId)
            parseLegacyError(legacyResults)?.let { throw Exception(it) }
            extendedResult.copy(results = legacyResults)
        } else {
            extendedResult
        }

        validateResultResponse(completedResult)

        CheckResponseDto(
            jobId = jobId,
            status = completedResult.statusLabel(),
            type = completedResult.command,
            target = completedResult.host,
            results = completedResult.results,
            payload = null,
            createdAt = completedResult.created.toIsoString(),
            updatedAt = null,
            context = null
        )
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

    private fun validateStartResponse(response: CheckHostStartResponseDto) {
        if (response.ok != 1 || response.requestId.isNullOrBlank()) {
            val message = response.error?.takeIf { it.isNotBlank() }
                ?: "Не удалось запустить проверку. Попробуйте позже."
            throw Exception(message)
        }
    }

    private fun validateResultResponse(response: CheckHostResultDto) {
        val message = response.error?.takeIf { it.isNotBlank() }
        if (message != null) throw Exception(message)
    }

    private fun CheckHostStartResponseDto.statusLabel(): String =
        if (ok == 1) "pending" else "error"

    private fun CheckHostResultDto.statusLabel(): String {
        if (!error.isNullOrBlank()) return "error"
        val resultElement = results
        if (resultElement == null || resultElement.isJsonNull) return "processing"

        if (resultElement.isJsonObject) {
            val entries = resultElement.asJsonObject.entrySet()
            val hasAnyCompletedNode = entries.any { (_, value) -> !value.isJsonNull }
            if (hasAnyCompletedNode) return "done"
            if (entries.isNotEmpty()) return "processing"
        }

        return "done"
    }

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

    private fun Long?.toIsoString(): String? = this?.let { Instant.ofEpochSecond(it).toString() }

    private fun JsonElement?.isNullMissingOrOnlyNullNodes(): Boolean {
        if (this == null || this.isJsonNull) return true
        if (!this.isJsonObject) return false

        val entries = this.asJsonObject.entrySet()
        if (entries.isEmpty()) return true

        val hasAnyValue = entries.any { (_, value) -> value != null && !value.isJsonNull }
        return !hasAnyValue
    }

    private fun parseLegacyError(result: JsonElement): String? {
        if (!result.isJsonObject) return null
        val obj = result.asJsonObject
        return when {
            obj.has("error") -> obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString
            obj.has("reason") -> obj.get("reason")?.takeIf { it.isJsonPrimitive }?.asString
            else -> null
        }
    }
}
