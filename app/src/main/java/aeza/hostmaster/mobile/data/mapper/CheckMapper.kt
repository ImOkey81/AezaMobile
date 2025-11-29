package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import javax.inject.Inject
import java.util.Locale

class CheckMapper @Inject constructor(
    private val gson: Gson
) {
    private val prettyGson: Gson by lazy(LazyThreadSafetyMode.NONE) {
        gson.newBuilder()
            .setPrettyPrinting()
            .create()
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

        if (type is CheckType.Http) {
            formatHttpDetails(element)?.let { return it }
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

        val extended = summary.toString().trim().takeIf { it.isNotEmpty() }
        if (extended != null) return extended

        return formatLegacyPingDetails(element)
    }

    private fun formatHttpDetails(element: JsonElement): String? {
        val source = when {
            element.isJsonObject && element.asJsonObject.has("results") -> element.asJsonObject.get("results")
            element.isJsonObject && element.asJsonObject.has("http") -> element.asJsonObject.get("http")
            else -> element
        } ?: return null

        if (!source.isJsonObject) return null

        val nodes = source.asJsonObject.entrySet()
        if (nodes.isEmpty()) return null

        val summary = StringBuilder()
        val pendingNodes = mutableListOf<String>()

        nodes.forEachIndexed { index, (nodeName, value) ->
            if (value == null || value.isJsonNull) {
                pendingNodes.add(nodeName)
                return@forEachIndexed
            }

            if (!value.isJsonArray) return@forEachIndexed

            val attempts = mutableListOf<HttpAttempt>()
            value.asJsonArray.forEach { outer ->
                if (outer.isJsonArray) {
                    outer.asJsonArray.forEach { inner ->
                        if (inner.isJsonArray) {
                            val attempt = parseHttpAttempt(inner.asJsonArray)
                            if (attempt != null) attempts.add(attempt)
                        }
                    }
                }
            }

            if (attempts.isEmpty()) return@forEachIndexed

            val preferredAttempt = attempts.firstOrNull { it.ok == true } ?: attempts.first()

            summary.appendLine(nodeName)

            preferredAttempt.statusLabel()?.let { summary.appendLine("Статус: $it") }
            preferredAttempt.latencyMs?.let { summary.appendLine("Время: ${formatMillis(it)} мс") }
            preferredAttempt.ip?.takeIf { it.isNotBlank() }?.let { summary.appendLine("IP: $it") }

            if (index < nodes.size - 1) summary.appendLine()
        }

        if (pendingNodes.isNotEmpty()) {
            summary.appendLine("Ожидание ответа: ${pendingNodes.joinToString(", ")}")
        }

        return summary.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun formatLegacyPingDetails(element: JsonElement): String? {
        if (!element.isJsonObject) return null

        val lines = StringBuilder()
        val nodes = element.asJsonObject.entrySet().filter { (_, value) -> value != null && !value.isJsonNull }
        if (nodes.isEmpty()) return null

        nodes.forEachIndexed { index, (nodeName, value) ->
            if (!value.isJsonArray) return@forEachIndexed

            val attempts = mutableListOf<JsonArray>()
            value.asJsonArray.forEach { outer ->
                if (outer.isJsonArray) {
                    outer.asJsonArray.forEach { inner ->
                        if (inner.isJsonArray) attempts.add(inner.asJsonArray)
                    }
                }
            }

            if (attempts.isEmpty()) return@forEachIndexed

            var ip: String? = null
            var successCount = 0
            val times = mutableListOf<Double>()

            attempts.forEach { attempt ->
                val status = attempt.readString(0)
                val timeSeconds = attempt.readDouble(1)
                val attemptIp = attempt.readString(2)
                if (ip == null && !attemptIp.isNullOrBlank()) ip = attemptIp

                if (status.equals("OK", ignoreCase = true)) {
                    successCount++
                    timeSeconds?.let { times.add(it) }
                }
            }

            val total = attempts.size
            val failed = total - successCount
            val avgMs = times.takeIf { it.isNotEmpty() }?.average()?.times(1000)

            lines.appendLine(nodeName)
            ip?.let { lines.appendLine("IP: $it") }
            lines.appendLine("Успешно: $successCount из $total${if (failed > 0) " (ошибок $failed)" else ""}")
            avgMs?.let { lines.appendLine("Среднее время: ${formatMillis(it)} мс") }

            if (index < nodes.size - 1) lines.appendLine()
        }

        return lines.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun formatMillis(value: Double): String {
        return if (value >= 100) {
            String.format(Locale.getDefault(), "%.0f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun parseHttpAttempt(array: JsonArray): HttpAttempt? {
        val ok = array.readBooleanLike(0)
        val latencyMs = array.readDouble(1)?.times(1000)
        val message = array.readString(2)
        val code = array.readString(3)
        val ip = array.readString(4)

        if (ok == null && latencyMs == null && message == null && code == null && ip == null) return null

        return HttpAttempt(ok, latencyMs, message, code, ip)
    }

    private fun JsonArray.readBooleanLike(index: Int): Boolean? {
        val primitive = getPrimitive(index) ?: return null
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asNumber.toInt() != 0
            primitive.isString -> primitive.asString.equals("ok", ignoreCase = true) || primitive.asString == "1"
            else -> null
        }
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

    private fun JsonArray.getPrimitive(index: Int): JsonPrimitive? {
        if (index >= size()) return null
        val element = get(index)
        return if (element != null && element.isJsonPrimitive && !element.isJsonNull) element.asJsonPrimitive else null
    }

    private fun JsonArray.readString(index: Int): String? {
        val primitive = getPrimitive(index) ?: return null
        return when {
            primitive.isString -> primitive.asString
            primitive.isBoolean -> primitive.asBoolean.toString()
            primitive.isNumber -> primitive.asNumber.toString()
            else -> null
        }
    }

    private fun JsonArray.readDouble(index: Int): Double? {
        val primitive = getPrimitive(index) ?: return null
        return when {
            primitive.isNumber -> primitive.asNumber.toDouble()
            primitive.isString -> primitive.asString.toDoubleOrNull()
            else -> null
        }
    }

    private data class HttpAttempt(
        val ok: Boolean?,
        val latencyMs: Double?,
        val message: String?,
        val code: String?,
        val ip: String?
    ) {
        fun statusLabel(): String? {
            val status = when (ok) {
                true -> "OK"
                false -> "Ошибка"
                else -> null
            }
            val details = listOfNotNull(
                code?.takeIf { it.isNotBlank() }?.let { "код $it" },
                message?.takeIf { it.isNotBlank() }
            )
            val suffix = details.takeIf { it.isNotEmpty() }?.joinToString(", ")
            return when {
                status != null && suffix != null -> "$status — $suffix"
                status != null -> status
                suffix != null -> suffix
                else -> null
            }
        }
    }
}
