package aeza.hostmaster.mobile.data.repository

import android.util.Log
import aeza.hostmaster.mobile.data.model.CheckRequestDto
import aeza.hostmaster.mobile.data.remote.ApiService
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

class CheckRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun submitCheck(target: String, type: String) =
        runCatching {
            val normalizedType = type.uppercase(Locale.ROOT)
            Log.d(TAG, "Submitting $normalizedType check for $target")
            api.submitCheck(
                CheckRequestDto(
                    target = target,
                    checkTypes = listOf(normalizedType)
                )
            ).also {
                Log.d(TAG, "Backend accepted job ${'$'}{it.jobId} for $normalizedType check")
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            throw when (throwable) {
                is HttpException -> {
                    val rawBody = runCatching { throwable.response()?.errorBody()?.string() }.getOrNull()
                    Log.w(
                        TAG,
                        "HTTP ${'$'}{throwable.code()} while submitting check: ${'$'}{throwable.message()}" +
                            (rawBody?.let { " | body=$it" } ?: ""),
                        throwable
                    )
                    IllegalArgumentException(
                        parseErrorMessage(rawBody)
                            ?: throwable.response()?.message()
                            ?: "Некорректный запрос",
                        throwable
                    )
                }
                is IOException -> {
                    Log.e(TAG, "Network error during check submission", throwable)
                    IOException("Не удалось подключиться к серверу", throwable)
                }
                else -> {
                    Log.e(TAG, "Unexpected error during check submission", throwable)
                    RuntimeException(throwable.message ?: "Неизвестная ошибка", throwable)
                }
            }
        }

    suspend fun getStatus(jobId: String) =
        api.getCheckStatus(jobId).also {
            Log.d(TAG, "Status for job $jobId: ${'$'}{it.status}")
        }

    private fun parseErrorMessage(rawBody: String?): String? {
        val raw = rawBody?.takeIf { it.isNotBlank() } ?: return null
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

    private companion object {
        private const val TAG = "CheckRepository"
    }
}
