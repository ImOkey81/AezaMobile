package aeza.hostmaster.mobile.data.model

data class CheckResponseDto(
    val jobId: String,
    val status: String,
    val results: Any?
)
