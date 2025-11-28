package aeza.hostmaster.mobile.data.model

import com.google.gson.annotations.SerializedName

data class CheckHostStartResponseDto(
    val ok: Int? = null,
    val error: String? = null,
    @SerializedName("request_id")
    val requestId: String? = null,
    @SerializedName("permanent_link")
    val permanentLink: String? = null,
    val nodes: com.google.gson.JsonElement? = null,
)
