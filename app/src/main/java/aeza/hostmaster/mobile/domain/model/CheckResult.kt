package aeza.hostmaster.mobile.domain.model

data class CheckResult(
    val jobId: String,
    val type: CheckType,
    val target: String?,
    val status: String,
    val details: String,
    val timestampMillis: Long
)

fun CheckResult.isTerminal(): Boolean {
    val inProgressStatuses = setOf(
        "pending", "queued", "running", "processing", "in_progress"
    )
    return status.lowercase() !in inProgressStatuses
}
