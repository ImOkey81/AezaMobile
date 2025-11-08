package aeza.hostmaster.mobile.data.model

import com.google.gson.annotations.SerializedName

data class CheckRequestDto(
    val target: String,
    val type: String,
    @SerializedName("check_type")
    val checkType: String
)
