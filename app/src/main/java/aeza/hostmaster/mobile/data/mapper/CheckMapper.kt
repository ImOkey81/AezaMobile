package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject

class CheckMapper @Inject constructor(
    private val gson: Gson
) {
    private val prettyGson: Gson by lazy(LazyThreadSafetyMode.NONE) {
        GsonBuilder().setPrettyPrinting().create()
    }

    fun toDomain(dto: CheckResponseDto, fallbackType: CheckType): CheckResult {
        val type = CheckType.fromBackendName(dto.type) ?: fallbackType
        val detailsElement = dto.results?.takeIf { !it.isJsonNull }
            ?: dto.payload?.takeIf { !it.isJsonNull }
        val normalizedDetails = detailsElement?.let { extractPayload(it) }
        val details = normalizedDetails?.let { element ->
            if (element.isJsonPrimitive) {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isString -> primitive.asString
                    primitive.isBoolean -> primitive.asBoolean.toString()
                    primitive.isNumber -> primitive.asNumber.toString()
                    else -> primitive.toString()
                }
            } else {
                gson.toJson(element)
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

    private tailrec fun extractPayload(element: JsonElement): JsonElement {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val nestedKeys = listOf("payload", "data", "result")
            for (key in nestedKeys) {
                if (obj.has(key)) {
                    val nested = obj.get(key)
                    if (nested != null && !nested.isJsonNull) {
                        return extractPayload(nested)
                    }
                }
            }
        }
        return element
    }
}
