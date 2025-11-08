package aeza.hostmaster.mobile.data.repository

import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.remote.ApiService
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

class CheckRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun submitCheck(target: String, type: String) =
        runCatching {
            val normalizedType = type.uppercase(Locale.ROOT)
            api.submitCheck(
                CheckRequestDto(
                    target = target,
                    type = normalizedType,
                    checkType = normalizedType
                )
            )
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            throw when (throwable) {
                is HttpException -> IllegalArgumentException(
                    parseErrorMessage(throwable.response()?.errorBody())
                        ?: throwable.response()?.message()
                        ?: "Некорректный запрос",
                    throwable
                )
                is IOException -> IOException("Не удалось подключиться к серверу", throwable)
                else -> RuntimeException(throwable.message ?: "Неизвестная ошибка", throwable)
            }
        }

    suspend fun getStatus(jobId: String) = api.getCheckStatus(jobId)

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
