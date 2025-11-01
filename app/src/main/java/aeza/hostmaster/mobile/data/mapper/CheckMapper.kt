package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import javax.inject.Inject

class CheckMapper @Inject constructor(
    private val gson: Gson
) {
    fun toDomain(dto: CheckResponseDto, fallbackType: CheckType): CheckResult {
        val type = CheckType.fromBackendName(dto.type) ?: fallbackType
        val details = dto.results?.let { results ->
            if (results.isJsonPrimitive) {
                results.asString
            } else {
                gson.toJson(results)
            }
        } ?: "Нет данных"

        val target = dto.target ?: dto.context?.let { context ->
            when {
                !context.host.isNullOrBlank() && context.port != null ->
                    "${context.host}:${context.port}"
                !context.host.isNullOrBlank() -> context.host
                !context.target.isNullOrBlank() -> context.target
                else -> null
            }
        }
        val timestamp = dto.createdAtMillis ?: System.currentTimeMillis()

        return CheckResult(
            jobId = dto.jobId,
            type = type,
            target = target,
            status = dto.status,
            details = details,
            timestampMillis = timestamp
        )
    }
}
