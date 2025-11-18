package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
        val details = normalizedDetails?.let { formatDetails(it, type) } ?: "Нет данных"

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

    private fun formatDetails(element: JsonElement, type: CheckType): String {
        if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            return when {
                primitive.isString -> primitive.asString
                primitive.isBoolean -> primitive.asBoolean.toString()
                primitive.isNumber -> primitive.asNumber.toString()
                else -> primitive.toString()
            }
        }

        if (type is CheckType.Ping) {
            formatPingDetails(element)?.let { return it }
        }

        return prettyGson.toJson(element)
    }

    private fun formatPingDetails(element: JsonElement): String? {
        val source = when {
            element.isJsonObject && element.asJsonObject.has("ping") -> element.asJsonObject.get("ping")
            else -> element
        } ?: return null

        val entries = when {
            source.isJsonArray -> source.asJsonArray
            source.isJsonObject -> JsonArray().apply { add(source) }
            else -> return null
        }

        if (entries.isEmpty) return null

        val summary = StringBuilder()
        entries.forEachIndexed { index, node ->
            if (!node.isJsonObject) return@forEachIndexed
            val obj = node.asJsonObject
            val lines = mutableListOf<String>()

            obj.readPrimitive("ip")?.takeIf { it.isNotBlank() }?.let { lines.add("IP: $it") }

            val locationParts = listOfNotNull(
                obj.readPrimitive("location")?.takeIf { it.isNotBlank() },
                obj.readPrimitive("country")?.takeIf { it.isNotBlank() }
            )
            if (locationParts.isNotEmpty()) {
                lines.add("Локация: ${locationParts.joinToString(" · ")}")
            }

            obj.getAsJsonObject("packets")?.let { packets ->
                val packetParts = mutableListOf<String>()
                packets.readPrimitive("loss")?.takeIf { it.isNotBlank() }
                    ?.let { packetParts.add("потери $it") }
                val counts = listOfNotNull(
                    packets.readPrimitive("transmitted")?.let { "отправлено $it" },
                    packets.readPrimitive("received")?.let { "получено $it" }
                )
                if (counts.isNotEmpty()) packetParts.add(counts.joinToString(", "))
                if (packetParts.isNotEmpty()) {
                    lines.add("Пакеты: ${packetParts.joinToString(", ")}")
                }
            }

            obj.getAsJsonObject("roundTrip")?.let { rtt ->
                val rttParts = listOfNotNull(
                    rtt.readPrimitive("avg")?.let { "ср. $it" },
                    rtt.readPrimitive("min")?.let { "мин. $it" },
                    rtt.readPrimitive("max")?.let { "макс. $it" }
                )
                if (rttParts.isNotEmpty()) {
                    lines.add("RTT: ${rttParts.joinToString(", ")}")
                }
            }

            if (lines.isNotEmpty()) {
                lines.forEach { summary.appendLine(it) }
                if (index < entries.size() - 1) summary.appendLine()
            }
        }

        return summary.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.readPrimitive(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive || element.isJsonNull) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> primitive.asString
            primitive.isBoolean -> primitive.asBoolean.toString()
            primitive.isNumber -> primitive.asNumber.toString()
            else -> null
        }
    }
}
