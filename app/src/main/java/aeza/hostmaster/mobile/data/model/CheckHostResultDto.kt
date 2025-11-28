package aeza.hostmaster.mobile.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class CheckHostResultDto(
    val command: String? = null,
    val created: Long? = null,
    val host: String? = null,
    @SerializedName("permanent_link")
    val permanentLink: String? = null,
    val results: JsonElement? = null,
    val error: String? = null,
)
