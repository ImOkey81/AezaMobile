package aeza.hostmaster.mobile.domain.model

data class CheckResult(
    val jobId: String,
    val status: String,
    val results: String
)