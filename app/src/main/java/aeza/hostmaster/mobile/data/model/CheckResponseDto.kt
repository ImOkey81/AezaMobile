package aeza.hostmaster.mobile.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class CheckResponseDto(
    @SerializedName(value = "jobId", alternate = ["job_id"])
    val jobId: String,
    @SerializedName(value = "status", alternate = ["state"])
    val status: String,
    @SerializedName(value = "type", alternate = ["check_type"])
    val type: String? = null,
    @SerializedName(value = "target", alternate = ["url", "host"])
    val target: String? = null,
    @SerializedName(value = "results", alternate = ["result"])
    val results: JsonElement? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"])
    val updatedAt: String? = null,
    val context: CheckResponseContextDto? = null
) {
    val createdAtMillis: Long?
        get() = createdAt?.let { parseDate(it) }
            ?: updatedAt?.let { parseDate(it) }

    private fun parseDate(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return formatter.parse(value)?.time
            } catch (_: ParseException) {
                // Try next pattern
            }
        }
        return null
    }
}
