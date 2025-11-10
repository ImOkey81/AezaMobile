package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlin.LazyThreadSafetyMode
import javax.inject.Inject

class CheckMapper @Inject constructor(
    private val gson: Gson
) {
    private val prettyGson: Gson by lazy(LazyThreadSafetyMode.NONE) {
        GsonBuilder().setPrettyPrinting().create()
    }

    fun toDomain(dto: CheckResponseDto, fallbackType: CheckType): CheckResult {
        val type = CheckType.fromBackendName(dto.type) ?: fallbackType
        val normalizedDetails = selectDetailsElement(dto)
        val details = normalizedDetails?.let { element ->
            val formatted = formatDetails(element)?.takeIf { it.isNotBlank() }
                ?: prettyPrint(element).trim()
            formatted.ifBlank { null }
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

    private fun selectDetailsElement(dto: CheckResponseDto): JsonElement? {
        val candidates = listOfNotNull(dto.results, dto.payload)
        for (candidate in candidates) {
            val element = candidate.takeIf { !it.isJsonNull } ?: continue
            val extracted = extractPayload(element)
            if (extracted.isMeaningful()) {
                return extracted
            }
        }
        return null
    }

    private tailrec fun extractPayload(element: JsonElement): JsonElement {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val raw = element.asString.trim()
            if (raw.startsWith("{") && raw.endsWith("}") || raw.startsWith("[") && raw.endsWith("]")) {
                val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull()
                if (parsed != null && !parsed.isJsonNull) {
                    return extractPayload(parsed)
                }
            }
            return element
        }

        if (element.isJsonArray) {
            val array = element.asJsonArray
            if (array.size() == 1) {
                val first = array[0]
                if (first != null && !first.isJsonNull) {
                    return extractPayload(first)
                }
            }
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val nestedKeys = listOf("payload", "data", "result", "details")
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

    private fun JsonElement.isMeaningful(): Boolean = when {
        isJsonNull -> false
        isJsonPrimitive -> {
            val primitive = asJsonPrimitive
            when {
                primitive.isString -> {
                    val value = primitive.asString.trim()
                    value.isNotEmpty() && !value.equals("null", ignoreCase = true) && !value.equals("undefined", ignoreCase = true)
                }
                primitive.isBoolean -> true
                primitive.isNumber -> true
                else -> false
            }
        }
        isJsonObject -> {
            val obj = asJsonObject
            obj.entrySet().any { (_, value) -> value != null && value.isMeaningful() }
        }
        isJsonArray -> {
            val array = asJsonArray
            array.any { element -> element != null && element.isMeaningful() }
        }
        else -> false
    }

    private fun formatDetails(element: JsonElement): String? {
        if (element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            return formatPrimitive(element.asJsonPrimitive)
        }

        if (element.isJsonObject) {
            val obj = element.asJsonObject
            formatPingDetails(obj)?.let { return it }
            return prettyGson.toJson(obj)
        }

        if (element.isJsonArray) {
            return prettyGson.toJson(element.asJsonArray)
        }

        return null
    }

    private fun formatPingDetails(root: JsonObject): String? {
        val pingArray = root.getAsArray("ping") ?: return null
        if (pingArray.size() == 0) return null

        val sections = mutableListOf<String>()
        pingArray.forEach { item ->
            if (item != null && item.isJsonObject) {
                val pingObject = item.asJsonObject
                val lines = mutableListOf<String>()

                val headerParts = listOfNotNull(
                    pingObject.getPrimitiveString("location"),
                    pingObject.getPrimitiveString("country")
                ).filter { it.isNotBlank() }
                if (headerParts.isNotEmpty()) {
                    lines += headerParts.joinToString(", ")
                }

                pingObject.getPrimitiveString("ip")?.let { ip ->
                    lines += "IP: $ip"
                }

                pingObject.getObject("packets")?.let { packets ->
                    val parts = mutableListOf<String>()
                    packets.getPrimitiveString("loss")?.let { parts += "потери $it" }
                    val transmitted = packets.getPrimitiveString("transmitted")
                    val received = packets.getPrimitiveString("received")
                    when {
                        !transmitted.isNullOrBlank() && !received.isNullOrBlank() ->
                            parts += "отправлено $transmitted / получено $received"
                        !transmitted.isNullOrBlank() -> parts += "отправлено $transmitted"
                        !received.isNullOrBlank() -> parts += "получено $received"
                    }
                    if (parts.isNotEmpty()) {
                        lines += "Пакеты: ${parts.joinToString(", ")}"
                    }
                }

                pingObject.getObject("roundTrip")?.let { roundTrip ->
                    val rttParts = mutableListOf<String>()
                    roundTrip.getPrimitiveString("avg")?.let { rttParts += "ср. $it" }
                    roundTrip.getPrimitiveString("min")?.let { rttParts += "мин. $it" }
                    roundTrip.getPrimitiveString("max")?.let { rttParts += "макс. $it" }
                    if (rttParts.isNotEmpty()) {
                        lines += "RTT: ${rttParts.joinToString(", ")}"
                    }
                }

                if (lines.isNotEmpty()) {
                    sections += lines.joinToString("\n")
                }
            }
        }

        return sections.filter { it.isNotBlank() }.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun JsonObject.getPrimitiveString(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> primitive.asString.trim().takeIf { it.isNotEmpty() }
            primitive.isBoolean -> primitive.asBoolean.toString()
            primitive.isNumber -> primitive.asNumber.toString()
            else -> null
        }
    }

    private fun JsonObject.getObject(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.getAsArray(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun prettyPrint(element: JsonElement): String = when {
        element.isJsonPrimitive -> formatPrimitive(element.asJsonPrimitive)
        element.isJsonObject -> prettyGson.toJson(element.asJsonObject)
        element.isJsonArray -> prettyGson.toJson(element.asJsonArray)
        else -> gson.toJson(element)
    }

    private fun formatPrimitive(primitive: JsonPrimitive): String = when {
        primitive.isString -> primitive.asString
        primitive.isBoolean -> primitive.asBoolean.toString()
        primitive.isNumber -> primitive.asNumber.toString()
        else -> primitive.toString()
    }
}
