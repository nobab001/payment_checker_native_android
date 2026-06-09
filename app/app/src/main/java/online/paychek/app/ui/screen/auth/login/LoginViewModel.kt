package online.paychek.app.ui.screen.auth.login

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.DeviceIdHelper

class LoginViewModel : ViewModel() {

    private val apiService = RetrofitClient.authApiService

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        fetchPublicConfigs()
    }

    fun fetchPublicConfigs() {
        viewModelScope.launch {
            try {
                val response = apiService.getPublicConfig()
                if (response.isSuccessful && response.body() != null) {
                    val configs = response.body()!!.configs
                    val isMaintenance = configs["maintenance_mode"] == "true"
                    _uiState.update {
                        it.copy(
                            isMaintenanceMode = isMaintenance,
                            whatsappSupportLink = configs["whatsapp_support_link"]?.takeIf { lnk -> lnk.isNotBlank() } ?: "",
                            telegramSupportLink = configs["telegram_support_link"]?.takeIf { lnk -> lnk.isNotBlank() } ?: "",
                            facebookSupportLink = configs["facebook_support_link"]?.takeIf { lnk -> lnk.isNotBlank() } ?: "",
                            youtubeSupportLink  = configs["youtube_support_link"]?.takeIf { lnk -> lnk.isNotBlank() } ?: ""
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore and use defaults
            }
        }
    }

    fun onContactChanged(contact: String) {
        _uiState.update { it.copy(contact = contact, errorMessage = null) }
    }

    fun onOtpChanged(otp: String) {
        val maxLen = if (_uiState.value.contact == "admin") 16 else 6
        if (otp.length <= maxLen) {
            _uiState.update { it.copy(otpCode = otp, errorMessage = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun checkContactAndRequestOtp(context: Context) {
        val contact = _uiState.value.contact.trim()
        if (contact.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "মোবাইল নম্বর অথবা ইমেইল লিখুন") }
            return
        }

        val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(contact).matches()
        val isPhone = contact.length >= 11 && contact.all { it.isDigit() }
        val isAdminBypass = contact == "admin"

        if (!isEmail && !isPhone && !isAdminBypass) {
            _uiState.update { it.copy(errorMessage = "সঠিক ১১-ডিজিটের মোবাইল নম্বর অথবা ইমেইল দিন") }
            return
        }

        val deviceId          = DeviceIdHelper.getHashedAndroidId(context)
        val fingerprint       = DeviceIdHelper.getHashedFingerprint()
        val androidId         = DeviceIdHelper.getAndroidId(context)
        val hardwareFingerprint = DeviceIdHelper.getBuildFingerprint()
        val simSlotIds        = DeviceIdHelper.getSimSlotIds(context)

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // ══════════════════════════════════════════════════════════
                // STEP 0 — DEVICE-BINDING GATEKEEPER (সবার আগে চলবে)
                // Policy:
                //   HTTP 403 → নিশ্চিতভাবে bound → block করো
                //   HTTP 200 + isLocked/!trialAllowed → block করো
                //   অন্য যেকোনো error (500, network fail) → proceed করো
                //   (false-blocking legitimate users is worse than a rare miss)
                // ══════════════════════════════════════════════════════════
                if (!isAdminBypass) {
                    try {
                        val trialResponse = apiService.checkDeviceTrial(
                            CheckDeviceTrialRequest(
                                deviceId            = deviceId,
                                fingerprint         = fingerprint,
                                androidId           = androidId,
                                hardwareFingerprint = hardwareFingerprint,
                                simSlotIds          = simSlotIds
                            )
                        )

                        when {
                            // ── ✅ HTTP 403: ডিভাইস বাউন্ড — hard block ──────
                            trialResponse.code() == 403 -> {
                                val errorBody = trialResponse.errorBody()?.string()
                                val (phones, emails) = parseBoundCredentials(errorBody)
                                _uiState.update {
                                    it.copy(
                                        isLoading              = false,
                                        showRegisterDialog     = false,
                                        showTrialExpiredDialog = true,
                                        boundPhones            = phones,
                                        boundEmails            = emails
                                    )
                                }
                                return@launch
                            }

                            // ── ✅ HTTP 200: body check ───────────────────────
                            trialResponse.isSuccessful -> {
                                val body = trialResponse.body()
                                if (body != null) {
                                    if (body.success == true && body.abused == false) {
                                        // clean device → proceed to step 1
                                    } else if (!body.trialAllowed || body.isLocked || body.abused == true) {
                                        _uiState.update {
                                            it.copy(
                                                isLoading              = false,
                                                showRegisterDialog     = false,
                                                showTrialExpiredDialog = true
                                            )
                                        }
                                        return@launch
                                    }
                                }
                                // trialAllowed = true → proceed
                            }

                            // ── ⚠️ অন্য যেকোনো error (500, network) → proceed ──
                            else -> {
                                // সার্ভার সাময়িক সমস্যায় — user-কে block করব না,
                                // check-contact-এ backend double-check আছে
                                android.util.Log.w(
                                    "DeviceGatekeeper",
                                    "check-device-trial returned ${trialResponse.code()} — proceeding with caution"
                                )
                            }
                        }
                    } catch (gateEx: Exception) {
                        // Network error — proceed, backend check-contact-এ double-check আছে
                        android.util.Log.w(
                            "DeviceGatekeeper",
                            "check-device-trial network error: ${gateEx.localizedMessage} — proceeding"
                        )
                    }
                }
                // ── ডিভাইস ক্লিন বা check করা সম্ভব হয়নি — পরবর্তী ধাপে ──

                // STEP 1 — কন্টাক্ট ডাটাবেজে আছে কি না চেক (Backend Double-Check সহ)
                val checkResponse = apiService.checkContact(
                    CheckContactRequest(
                        contact             = contact,
                        deviceId            = deviceId,
                        fingerprint         = fingerprint,
                        androidId           = androidId,
                        hardwareFingerprint = hardwareFingerprint,
                        simSlotIds          = simSlotIds
                    )
                )
                if (checkResponse.isSuccessful && checkResponse.body() != null) {
                    val exists = checkResponse.body()!!.exists
                    _uiState.update { it.copy(isNewUser = !exists) }

                    if (!exists) {
                        // নতুন ইউজার → "নতুন অ্যাকাউন্ট তৈরি করুন" ডায়ালগ
                        _uiState.update {
                            it.copy(
                                isLoading          = false,
                                showRegisterDialog = true
                            )
                        }
                    } else {
                        // পুরনো ইউজার → OTP পাঠাও
                        sendOtpApi(
                            contact = contact,
                            deviceId = deviceId,
                            isNew = false,
                            androidId = androidId,
                            hardwareFingerprint = hardwareFingerprint,
                            simSlotIds = simSlotIds
                        )
                    }
                } else if (checkResponse.code() == 403) {
                    // Backend double-check: ডিভাইস বাউন্ড থাকলে এখানেও ব্লক
                    val errorBody = checkResponse.errorBody()?.string()
                    val (phones, emails) = parseBoundCredentials(errorBody)
                    _uiState.update {
                        it.copy(
                            isLoading              = false,
                            showRegisterDialog     = false, // "নতুন অ্যাকাউন্ট" ডায়ালগ নিষিদ্ধ
                            showTrialExpiredDialog = true,
                            boundPhones            = phones,
                            boundEmails            = emails
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "সার্ভার রেসপন্স করছে না। অনুগ্রহ করে পরে চেষ্টা করুন।")
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "নেটওয়ার্ক ত্রুটি: ${e.localizedMessage ?: "সংযোগ ব্যর্থ"}")
                }
            }
        }
    }

    private suspend fun sendOtpApi(
        contact: String,
        deviceId: String,
        isNew: Boolean,
        androidId: String? = null,
        hardwareFingerprint: String? = null,
        simSlotIds: String? = null
    ) {
        val request = SendOtpRequest(
            contact = contact,
            deviceId = deviceId,
            androidId = androidId,
            hardwareFingerprint = hardwareFingerprint,
            simSlotIds = simSlotIds
        )
        val response = if (isNew) {
            apiService.registerSendOtp(request)
        } else {
            apiService.sendOtp(request)
        }

        if (response.isSuccessful && response.body()?.success == true) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isOtpSent = true,
                    otpCode = "",
                    timerSeconds = 60
                )
            }
            startTimer()
        } else {
            val errorBody = response.errorBody()?.string()
            if (response.code() == 403 && errorBody?.contains("TRIAL_EXPIRED_FOR_DEVICE") == true) {
                val (phones, emails) = parseBoundCredentials(errorBody)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showTrialExpiredDialog = true,
                        boundPhones = phones,
                        boundEmails = emails
                    )
                }
                return
            }

            if (response.code() == 404) {
                if (errorBody?.contains("SHOW_NOT_FOUND_DIALOG") == true) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            showRegisterDialog = true
                        )
                    }
                    return
                }
            }

            val rawErrorMsg = try {
                val gson = com.google.gson.Gson()
                val map = gson.fromJson(errorBody, Map::class.java)
                map["message"] as? String ?: map["error"] as? String ?: response.body()?.message ?: "ওটিপি পাঠাতে ব্যর্থ হয়েছে।"
            } catch (e: Exception) {
                response.body()?.message ?: "ওটিপি পাঠাতে ব্যর্থ হয়েছে।"
            }

            val errorMsg = when {
                rawErrorMsg.contains("মোবাইল নম্বরের মাধ্যমে") -> {
                    if (isNew) {
                        "এই মুহূর্তে ইমেইল ওটিপি সার্ভারে সাময়িক সমস্যা হচ্ছে। অনুগ্রহ করে আপনার মোবাইল নম্বর ব্যবহার করে অ্যাকাউন্ট তৈরি করুন।"
                    } else {
                        "এই মুহূর্তে ইমেইল ওটিপি সার্ভারে সাময়িক সমস্যা হচ্ছে। অনুগ্রহ করে আপনার মোবাইল নম্বর ব্যবহার করে প্রবেশ করুন।"
                    }
                }
                rawErrorMsg.contains("জিমেইল/ইমেইলের মাধ্যমে") -> {
                    if (isNew) {
                        "মোবাইল ওটিপি ডেলিভারিতে সমস্যা হচ্ছে। অনুগ্রহ করে আপনার ইমেইল ঠিকানা ব্যবহার করে নিবন্ধন সম্পন্ন করুন।"
                    } else {
                        "মোবাইল ওটিপি ডেলিভারিতে সমস্যা হচ্ছে। অনুগ্রহ করে আপনার ইমেইল ঠিকানা ব্যবহার করে প্রবেশ করুন।"
                    }
                }
                else -> rawErrorMsg
            }

            _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
        }
    }

    fun dismissRegisterDialog() {
        _uiState.update { it.copy(showRegisterDialog = false) }
    }

    fun dismissTrialExpiredDialog() {
        _uiState.update { it.copy(showTrialExpiredDialog = false) }
    }

    fun proceedToRegister(context: Context) {
        val contact = _uiState.value.contact.trim()
        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val fingerprint = DeviceIdHelper.getHashedFingerprint()
        val androidId = DeviceIdHelper.getAndroidId(context)
        val hardwareFingerprint = DeviceIdHelper.getBuildFingerprint()
        val simSlotIds = DeviceIdHelper.getSimSlotIds(context)

        _uiState.update {
            it.copy(
                isLoading = true,
                showRegisterDialog = false,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // 1. Perform Device Trial Check to prevent trial abuse
                val trialResponse = apiService.checkDeviceTrial(
                    CheckDeviceTrialRequest(
                        deviceId = deviceId,
                        fingerprint = fingerprint,
                        androidId = androidId,
                        hardwareFingerprint = hardwareFingerprint,
                        simSlotIds = simSlotIds
                    )
                )

                if (trialResponse.isSuccessful && trialResponse.body() != null) {
                    val trialData = trialResponse.body()!!
                    if (trialData.success == true && trialData.abused == false) {
                        // clean device → proceed
                    } else if (!trialData.trialAllowed || trialData.isLocked || trialData.abused == true) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showTrialExpiredDialog = true
                            )
                        }
                        return@launch
                    }
                } else if (trialResponse.code() == 403) {
                    val errorBody = trialResponse.errorBody()?.string()
                    val isTrialAbused = errorBody?.contains("TRIAL_EXPIRED_FOR_DEVICE") == true
                    if (isTrialAbused) {
                        val (phones, emails) = parseBoundCredentials(errorBody)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showTrialExpiredDialog = true,
                                boundPhones = phones,
                                boundEmails = emails
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "ডিভাইস ট্রায়াল যাচাই করতে ব্যর্থ হয়েছে।"
                            )
                        }
                    }
                    return@launch
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "ডিভাইস ট্রায়াল যাচাই করতে ব্যর্থ হয়েছে। আবার চেষ্টা করুন।"
                        )
                    }
                    return@launch
                }

                // 2. Send Registration OTP
                _uiState.update { it.copy(isNewUser = true) }
                sendOtpApi(
                    contact = contact,
                    deviceId = deviceId,
                    isNew = true,
                    androidId = androidId,
                    hardwareFingerprint = hardwareFingerprint,
                    simSlotIds = simSlotIds
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "রেজিস্ট্রেশন ত্রুটি: ${e.localizedMessage ?: "সংযোগ ব্যর্থ"}"
                    )
                }
            }
        }
    }

    fun verifyOtp(context: Context, onOtpVerified: (VerifyOtpResponse) -> Unit) {
        val contact = _uiState.value.contact.trim()
        val code = _uiState.value.otpCode.trim()

        val isBypass = contact == "admin"
        if (!isBypass && code.length != 6) {
            _uiState.update { it.copy(errorMessage = "৬-ডিজিটের ওটিপি কোড লিখুন") }
            return
        }
        if (isBypass && code.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "এডমিন পাসওয়ার্ড লিখুন") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        val deviceId = DeviceIdHelper.getHashedAndroidId(context)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = Build.VERSION.RELEASE
        val fingerprint = DeviceIdHelper.getHashedFingerprint()
        val androidId = DeviceIdHelper.getAndroidId(context)
        val hardwareFingerprint = DeviceIdHelper.getBuildFingerprint()
        val simSlotIds = DeviceIdHelper.getSimSlotIds(context)

        viewModelScope.launch {
            try {
                val request = VerifyOtpRequest(
                    contact = contact,
                    code = code,
                    deviceId = deviceId,
                    deviceModel = deviceModel,
                    androidVersion = androidVersion,
                    fingerprint = fingerprint,
                    androidId = androidId,
                    hardwareFingerprint = hardwareFingerprint,
                    simSlotIds = simSlotIds
                )

                val response = apiService.verifyOtp(request)
                if (response.isSuccessful && response.body() != null) {
                    val verifyResponse = response.body()!!
                    _uiState.update { it.copy(isLoading = false) }
                    onOtpVerified(verifyResponse)
                } else {
                    val errorBody = response.errorBody()?.string()
                    if (response.code() == 403 && errorBody?.contains("TRIAL_EXPIRED_FOR_DEVICE") == true) {
                        val (phones, emails) = parseBoundCredentials(errorBody)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showTrialExpiredDialog = true,
                                boundPhones = phones,
                                boundEmails = emails
                            )
                        }
                    } else {
                        val rawMsg = try {
                            val gson = com.google.gson.Gson()
                            val map = gson.fromJson(errorBody, Map::class.java)
                            map["message"] as? String ?: map["error"] as? String ?: "ওটিপি কোডটি সঠিক নয়। অনুগ্রহ করে আবার চেক করুন।"
                        } catch (e: Exception) {
                            "ওটিপি কোডটি সঠিক নয়। অনুগ্রহ করে আবার চেক করুন।"
                        }
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = rawMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "ওটিপি যাচাই ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক এরর"}"
                    )
                }
            }
        }
    }

    fun resendOtp(context: Context) {
        if (_uiState.value.timerSeconds > 0) return
        checkContactAndRequestOtp(context)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timerSeconds > 0) {
                delay(1000L)
                _uiState.update { it.copy(timerSeconds = it.timerSeconds - 1) }
            }
        }
    }

    /**
     * Parses `boundPhones` and `boundEmails` arrays from the TRIAL_EXPIRED_FOR_DEVICE JSON error body.
     * Returns Pair(phones, emails) where each is an empty list if the field is absent or parsing fails.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseBoundCredentials(errorBody: String?): Pair<List<String>, List<String>> {
        if (errorBody.isNullOrBlank()) return Pair(emptyList(), emptyList())
        return try {
            val gson = com.google.gson.Gson()
            val map = gson.fromJson(errorBody, Map::class.java) as? Map<String, Any>
            val phones = (map?.get("boundPhones") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val emails = (map?.get("boundEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            Pair(phones, emails)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

data class LoginUiState(
    val contact: String = "",
    val isOtpSent: Boolean = false,
    val otpCode: String = "",
    val timerSeconds: Int = 0,
    val isLoading: Boolean = false,
    val isNewUser: Boolean = false,
    val isTrialBlocked: Boolean = false,
    val showRegisterDialog: Boolean = false,
    val showTrialExpiredDialog: Boolean = false,
    val boundPhones: List<String> = emptyList(),
    val boundEmails: List<String> = emptyList(),
    val errorMessage: String? = null,
    val isMaintenanceMode: Boolean = false,
    val whatsappSupportLink: String = "https://wa.me/8801700000000",
    val telegramSupportLink: String = "https://t.me/paychek_support",
    val facebookSupportLink: String = "https://facebook.com",
    val youtubeSupportLink: String = "https://youtube.com"
)
