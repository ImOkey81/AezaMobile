package aeza.hostmaster.mobile.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class CheckHostResultDto(
    val ok: Int? = null,
    val error: String? = null,
    val state: String? = null,
    val status: String? = null,
    @SerializedName("permanent_link")
    val permanentLink: String? = null,
    val result: JsonElement? = null,
)
