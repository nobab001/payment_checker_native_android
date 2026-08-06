package online.paychek.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AccountEntitlementsDto(
    @SerializedName("perm_custom_sender") val permCustomSender: Int = 0,
    @SerializedName("perm_template") val permTemplate: Int = 0,
    @SerializedName("perm_website") val permWebsite: Int = 0,
    @SerializedName("perm_device") val permDevice: Int = 0,
    @SerializedName("perm_smart_popup") val permSmartPopup: Int = 0,
    @SerializedName("perm_manual_transaction") val permManualTransaction: Int = 0,
    @SerializedName("eff_max_devices") val effMaxDevices: Int = 0,
    @SerializedName("eff_max_sites") val effMaxSites: Int = 0,
    /** Comm Policy v1.0 — welcome|personal|personal_business|gateway */
    @SerializedName("comm_profile") val commProfile: String? = null,
    @SerializedName("heartbeat") val heartbeatSec: Int? = null,
    @SerializedName("use_socket") val useSocket: Boolean? = null,
    @SerializedName("subscription_status") val subscriptionStatus: String? = null,
    @SerializedName("suspended") val suspended: Boolean? = null,
    @SerializedName("custom_all_chip_label") val customAllChipLabel: String? = null,
    @SerializedName("custom_all_popup_title") val customAllPopupTitle: String? = null,
    @SerializedName("custom_all_popup_notice") val customAllPopupNotice: String? = null
) {
    val hasCustomSender: Boolean get() = permCustomSender == 1
    val hasTemplate: Boolean get() = permTemplate == 1
    val hasWebsite: Boolean get() = permWebsite == 1
    val hasDevice: Boolean get() = permDevice == 1
    val hasSmartPopup: Boolean get() = permSmartPopup == 1
    val hasManualTransaction: Boolean get() = permManualTransaction == 1
}

data class AccountEntitlementsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("entitlements") val entitlements: AccountEntitlementsDto?
)
