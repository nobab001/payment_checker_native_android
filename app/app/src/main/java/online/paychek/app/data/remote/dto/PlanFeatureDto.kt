package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PlanFeatureDto(
    @SerializedName("text") val text: String,
    @SerializedName("icon") val icon: String = ICON_CHECK
) {
    companion object {
        const val ICON_CHECK = "check"
        const val ICON_CROSS = "cross"
    }
}
