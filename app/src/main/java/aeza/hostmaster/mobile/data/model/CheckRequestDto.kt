package aeza.hostmaster.mobile.data.model

import com.google.gson.annotations.SerializedName

/**
 * Payload used to request a new site check job from the backend.
 * The backend expects a list of check types under the `checkTypes` key,
 * matching its `SiteCheckCreateRequest` contract.
 */
data class CheckRequestDto(
    val target: String,
    val type: String,
    @SerializedName(value = "checkType", alternate = ["check_type"])
    val checkType: String
)
