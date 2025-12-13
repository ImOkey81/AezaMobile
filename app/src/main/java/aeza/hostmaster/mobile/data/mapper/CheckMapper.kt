package aeza.hostmaster.mobile.data.mapper

import aeza.hostmaster.mobile.data.model.CheckResponseDto
import aeza.hostmaster.mobile.domain.model.CheckResult
import aeza.hostmaster.mobile.domain.model.CheckType
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import javax.inject.Inject
import java.util.Locale

class CheckMapper @Inject constructor(
    private val gson: Gson,
) {
    private val prettyGson: Gson by lazy(LazyThreadSafetyMode.NONE) {
        gson.newBuilder()
            .setPrettyPrinting()
            .create()
    }

    fun toDomain(dto: CheckResponseDto, fallbackType: CheckType): CheckResult {
        val jobId = dto.jobId ?: throw IllegalStateException(
            "Отсутствует идентификатор задачи. Попробуйте повторить запрос."
        )
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
            jobId = jobId,
            type = type,
            target = target,
            status = dto.status,
            details = details,
            timestampMillis = timestamp,
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
        val entries: List<JsonObject> = when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { unwrapPingNode(it) }
            element.isJsonObject -> listOfNotNull(unwrapPingNode(element))
            else -> emptyList()
        }

        if (entries.isEmpty()) return null

        val summary = StringBuilder()
        entries.forEachIndexed { index, obj ->
            val lines = mutableListOf<String>()

            obj.readPrimitive("ip")?.takeIf { it.isNotBlank() }?.let { lines.add("IP: $it") }

            val locationParts = listOfNotNull(
                obj.readPrimitive("location")?.takeIf { it.isNotBlank() },
                obj.readPrimitive("country")?.takeIf { it.isNotBlank() },
            )
            if (locationParts.isNotEmpty()) {
                lines.add("Локация: ${locationParts.joinToString(" · ")}")
            }

            val packetParts = mutableListOf<String>()
            obj.getAsJsonObject("packets")?.let { packets ->
                packets.readPrimitive("loss")?.takeIf { it.isNotBlank() }
                    ?.let { packetParts.add("потери $it") }
                val counts = listOfNotNull(
                    packets.readPrimitive("transmitted")?.let { "отправлено $it" },
                    packets.readPrimitive("received")?.let { "получено $it" },
                )
                if (counts.isNotEmpty()) packetParts.add(counts.joinToString(", "))
            }

            val transmitted = obj.readPrimitive("transmitted")
            val received = obj.readPrimitive("received")
            val loss = obj.readPrimitive("packetLoss") ?: obj.readPrimitive("loss")
            val inlineCounts = listOfNotNull(
                transmitted?.let { "отправлено $it" },
                received?.let { "получено $it" },
            )
            if (inlineCounts.isNotEmpty()) packetParts.add(inlineCounts.joinToString(", "))
            loss?.let { packetParts.add("потери $it") }
            if (packetParts.isNotEmpty()) {
                lines.add("Пакеты: ${packetParts.joinToString(", ")}")
            }

            obj.getAsJsonObject("roundTrip")?.let { rtt ->
                val rttParts = listOfNotNull(
                    rtt.readPrimitive("avg")?.let { "ср. $it" },
                    rtt.readPrimitive("min")?.let { "мин. $it" },
                    rtt.readPrimitive("max")?.let { "макс. $it" },
                )
                if (rttParts.isNotEmpty()) {
                    lines.add("RTT: ${rttParts.joinToString(", ")}")
                }
            }

            val rttParts = listOfNotNull(
                obj.readPrimitive("avgRtt")?.let { "ср. $it" },
                obj.readPrimitive("minRtt")?.let { "мин. $it" },
                obj.readPrimitive("maxRtt")?.let { "макс. $it" },
            )
            if (rttParts.isNotEmpty()) {
                lines.add("RTT: ${rttParts.joinToString(", ")}")
            }

            if (lines.isNotEmpty()) {
                lines.forEach { summary.appendLine(it) }
                if (index < entries.lastIndex) summary.appendLine()
            }
        }

        val extended = summary.toString().trim().takeIf { it.isNotEmpty() }
        if (extended != null) return extended

        return formatLegacyPingDetails(element)
    }

    private fun unwrapPingNode(node: JsonElement): JsonObject? {
        if (!node.isJsonObject) return null
        val obj = node.asJsonObject
        val ping = obj.get("ping")
        return when {
            ping != null && ping.isJsonObject -> ping.asJsonObject
            else -> obj
        }
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

                if (status?.equals("OK", ignoreCase = true) == true) {
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

    private fun formatHttpDetails(element: JsonElement): String? {
        val attempts = extractHttpAttempts(element)

        if (attempts.isNotEmpty()) {
            val builder = StringBuilder()
            attempts.forEachIndexed { index, attempt ->
                val parts = mutableListOf<String>()
                attempt.code?.takeIf { it.isNotBlank() }?.let { parts.add("код $it") }
                attempt.ip?.takeIf { it.isNotBlank() }?.let { parts.add("IP $it") }
                attempt.latencyMs?.let { parts.add("время ${formatMillis(it)} мс") }
                attempt.message?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                attempt.ok?.let { parts.add(if (it) "успех" else "ошибка") }

                if (parts.isNotEmpty()) {
                    builder.appendLine(parts.joinToString(", "))
                    if (index < attempts.lastIndex) builder.appendLine()
                }
            }
            return builder.toString().trim().takeIf { it.isNotEmpty() }
        }

        if (element.isJsonObject) {
            val message = element.asJsonObject.readPrimitive("message")
                ?: element.asJsonObject.readPrimitive("status")
            if (!message.isNullOrBlank()) return message
        }

        return null
    }

    private fun extractHttpAttempts(element: JsonElement): List<HttpAttempt> {
        val attempts = mutableListOf<HttpAttempt>()

        when {
            element.isJsonArray -> {
                val array = element.asJsonArray

                array.forEach { candidate ->
                    when {
                        candidate.isJsonArray -> {
                            val inner = candidate.asJsonArray
                            var consumedNested = false
                            inner.forEach { nested ->
                                if (nested.isJsonArray) {
                                    parseHttpAttempt(nested.asJsonArray)?.let { attempts.add(it) }
                                    consumedNested = true
                                }
                            }
                            if (!consumedNested) {
                                parseHttpAttempt(inner)?.let { attempts.add(it) }
                            }
                        }

                        candidate.isJsonObject -> {
                            parseHttpAttempt(candidate.asJsonObject)?.let { attempts.add(it) }
                        }
                    }
                }

                if (attempts.isEmpty()) {
                    parseHttpAttempt(array)?.let { attempts.add(it) }
                }
            }

            element.isJsonObject -> {
                val obj = element.asJsonObject
                when (val attemptsNode = obj.get("attempts")) {
                    is JsonArray -> attempts.addAll(extractHttpAttempts(attemptsNode))
                    is JsonObject -> attemptsNode.entrySet()
                        .mapNotNull { (_, value) -> value }
                        .forEach { value ->
                            when {
                                value.isJsonObject -> parseHttpAttempt(value.asJsonObject)?.let { attempts.add(it) }
                                value.isJsonArray -> parseHttpAttempt(value.asJsonArray)?.let { attempts.add(it) }
                            }
                        }
                }

                if (attempts.isEmpty()) {
                    parseHttpAttempt(obj)?.let { attempts.add(it) }
                }
            }
        }

        return attempts
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

    private fun parseHttpAttempt(obj: JsonObject): HttpAttempt? {
        val ok = obj.readBooleanLike("ok")
            ?: obj.readBooleanLike("success")
            ?: obj.readBooleanLike("status")

        val latencyMs = obj.readDurationMs("latencyMs")
            ?: obj.readDurationMs("latency_ms")
            ?: obj.readDurationMs("latency")
            ?: obj.readDurationMs("duration")
            ?: obj.readDurationMs("time")

        val message = obj.readPrimitive("message")
            ?: obj.readPrimitive("detail")
            ?: obj.readPrimitive("error")
            ?: obj.readPrimitive("status")

        val code = obj.readPrimitive("code") ?: obj.readPrimitive("statusCode")
        val ip = obj.readPrimitive("ip") ?: obj.readPrimitive("address")

        if (ok == null && latencyMs == null && message == null && code == null && ip == null) return null

        return HttpAttempt(ok, latencyMs, message, code, ip)
    }

    private data class HttpAttempt(
        val ok: Boolean?,
        val latencyMs: Double?,
        val message: String?,
        val code: String?,
        val ip: String?,
    )

    private fun JsonObject.readDurationMs(key: String): Double? {
        val raw = readDouble(key) ?: return null
        return if (key.lowercase(Locale.getDefault()).contains("sec")) raw * 1000 else raw
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

    private fun JsonArray.getPrimitive(index: Int): JsonPrimitive? {
        if (index >= size()) return null
        val element = get(index)
        return if (element != null && element.isJsonPrimitive && !element.isJsonNull) element.asJsonPrimitive else null
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

    private fun JsonObject.readBooleanLike(key: String): Boolean? {
        val primitive = get(key)
        if (primitive == null || !primitive.isJsonPrimitive || primitive.isJsonNull) return null
        val value = primitive.asJsonPrimitive
        return when {
            value.isBoolean -> value.asBoolean
            value.isNumber -> value.asNumber.toInt() != 0
            value.isString -> value.asString.equals("ok", ignoreCase = true) || value.asString == "1"
            else -> null
        }
    }

    private fun JsonObject.readDouble(key: String): Double? {
        val primitive = get(key)
        if (primitive == null || !primitive.isJsonPrimitive || primitive.isJsonNull) return null
        val value = primitive.asJsonPrimitive
        return when {
            value.isNumber -> value.asNumber.toDouble()
            value.isString -> value.asString.toDoubleOrNull()
            else -> null
        }
    }
}
