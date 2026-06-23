package online.paychek.app.ui.screen.device

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.data.remote.dto.*
import online.paychek.app.utils.SecurePreferences
import online.paychek.app.utils.NetworkConnectivityObserver
import online.paychek.app.utils.DeviceIdHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

// =============================================================================
// UI State
// =============================================================================
data class DeviceUiState(
    val methods:          List<GatewayMethod> = emptyList(),
    val sim1Enabled:      Boolean             = true,
    val sim2Enabled:      Boolean             = true,
    val isLoading:        Boolean             = true,
    val isSaving:         Boolean             = false,
    val errorMessage:     String?             = null,
    val successMessage:   String?             = null,
    // Bottom Sheet
    val editingMethod:    GatewayMethod?      = null,
    val editNumber:       String              = "",
    val editDisplayName:  String              = "",
    
    // Sub tabs & Remote Devices
    val selectedSubTab:   Int                 = 0, // 0 = ডিভাইস সেটিং, 1 = আদার্স ডিভাইস
    val childDevices:     List<ChildDeviceDto> = emptyList(),
    val isChildDevicesLoading: Boolean        = false,
    val activeRemoteDevice: ChildDeviceDto?   = null,
    val remoteDeviceEditName: String          = "",
    val remoteDeviceEditSim1Number: String    = "",
    val remoteDeviceEditSim2Number: String    = "",
    val remoteDeviceEditSim1Active: Boolean   = true,
    val remoteDeviceEditSim2Active: Boolean   = true,
    val remoteDeviceEditAppActive: Boolean    = true,
    val remoteDeviceEditPin: String           = "",
    
    // New Role Toggle States
    val showRolePinDialog: Boolean            = false,
    val rolePinInput: String                  = "",
    val rolePinError: String?                 = null,
    val deviceForRoleToggle: ChildDeviceDto?  = null,
    val targetRoleToggleValue: String         = "",
    
    // Redesign Device Settings UI fields
    val templates: List<SmsTemplateDto>       = emptyList(),
    val sim1Number: String                    = "",
    val sim2Number: String                    = "",
    val isTemplatesLoading: Boolean           = false
)

// =============================================================================
// ViewModel
// =============================================================================
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val api   = RetrofitClient.gatewayApiService
    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val connectivityObserver = NetworkConnectivityObserver(application)

    val isNetworkAvailable = connectivityObserver.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = connectivityObserver.isNetworkAvailable()
        )

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    init {
        val sim1 = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
        val sim2 = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)
        
        // Load cached methods to show offline/instantly
        val cachedJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(application)
        val cachedList = if (cachedJson.isNotEmpty() && cachedJson != "[]") {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
                com.google.gson.Gson().fromJson<List<GatewayMethod>>(cachedJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        val cachedSim1Num = cachedList.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number ?: ""
        val cachedSim2Num = cachedList.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number ?: ""

        _state.update { 
            it.copy(
                sim1Enabled = sim1, 
                sim2Enabled = sim2, 
                methods = cachedList,
                sim1Number = cachedSim1Num,
                sim2Number = cachedSim2Num,
                isLoading = cachedList.isEmpty()
            ) 
        }

        viewModelScope.launch {
            isNetworkAvailable.collect { available ->
                if (available) {
                    loadGatewayMethods()
                    loadTemplates()
                    if (_state.value.selectedSubTab == 1) {
                        loadChildDevices()
                    }
                }
            }
        }
    }

    private fun saveMethodsToCache(methods: List<GatewayMethod>) {
        try {
            val json = com.google.gson.Gson().toJson(methods)
            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(getApplication(), json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadGatewayMethods() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching { api.getGatewayMethods("Bearer $token") }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val sorted = (res.body()!!.data).sortedBy { it.priority }
                        val sim1Num = sorted.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number ?: _state.value.sim1Number
                        val sim2Num = sorted.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number ?: _state.value.sim2Number
                        _state.update { 
                            it.copy(
                                methods = sorted, 
                                sim1Number = sim1Num,
                                sim2Number = sim2Num,
                                isLoading = false
                            ) 
                        }
                        saveMethodsToCache(sorted)
                        validateAndSyncSimToggles()
                        performDropSync()
                    } else {
                        setError("মেথড লোড ব্যর্থ হয়েছে (${res.code()})")
                    }
                }
                .onFailure { setError("নেটওয়ার্ক সমস্যা: ${it.message}") }
        }
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val current = _state.value.methods.toMutableList()
        if (fromIndex == toIndex ||
            fromIndex < 0 || toIndex < 0 ||
            fromIndex >= current.size || toIndex >= current.size) return

        current.add(toIndex, current.removeAt(fromIndex))
        _state.update { it.copy(methods = current) }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500L)
            savePriorityOrder(current)
        }
    }

    private suspend fun savePriorityOrder(methods: List<GatewayMethod>) {
        _state.update { it.copy(isSaving = true) }
        val token = getToken() ?: return

        val items = methods.mapIndexed { idx, m ->
            PriorityItem(id = m.id, priority = idx + 1)
        }

        runCatching {
            api.updatePriority("Bearer $token", UpdatePriorityRequest(items))
        }.onSuccess { res ->
            if (res.isSuccessful && res.body()?.success == true) {
                res.body()?.data?.let { newData ->
                    saveMethodsToCache(newData)
                    _state.update { it.copy(methods = newData) }
                }
            }
            _state.update {
                it.copy(
                    isSaving       = false,
                    successMessage = if (res.isSuccessful) "ক্রম সেভ হয়েছে ✓" else null
                )
            }
            viewModelScope.launch {
                delay(2000)
                _state.update { it.copy(successMessage = null) }
            }
        }.onFailure {
            _state.update { it.copy(isSaving = false) }
        }
    }

    fun toggleMethod(method: GatewayMethod) {
        val newEnabled = if (method.isEnabled == 1) 0 else 1

        _state.update { current ->
            val updated = current.methods.map {
                if (it.id == method.id) it.copy(isEnabled = newEnabled) else it
            }
            saveMethodsToCache(updated)
            current.copy(methods = updated)
        }
        validateAndSyncSimToggles()

        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching {
                api.toggleMethod("Bearer $token", method.id, ToggleRequest(newEnabled))
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    res.body()?.data?.let { newData ->
                        saveMethodsToCache(newData)
                        _state.update { it.copy(methods = newData) }
                    }
                }
                validateAndSyncSimToggles()
            }.onFailure {
                _state.update { current ->
                    val updated = current.methods.map {
                        if (it.id == method.id) it.copy(isEnabled = method.isEnabled) else it
                    }
                    saveMethodsToCache(updated)
                    current.copy(methods = updated)
                }
                validateAndSyncSimToggles()
            }
        }
    }

    fun toggleSim(simSlot: Int) {
        val isCurrentlyEnabled = if (simSlot == 1) _state.value.sim1Enabled
                                 else               _state.value.sim2Enabled
        val newValue = !isCurrentlyEnabled

        _state.update {
            if (simSlot == 1) {
                prefs.edit().putBoolean(AppConfig.KEY_SIM1_ENABLED, newValue).apply()
                it.copy(sim1Enabled = newValue)
            } else {
                prefs.edit().putBoolean(AppConfig.KEY_SIM2_ENABLED, newValue).apply()
                it.copy(sim2Enabled = newValue)
            }
        }
    }

    fun openEditSheet(method: GatewayMethod) {
        _state.update {
            it.copy(
                editingMethod   = method,
                editNumber      = method.number ?: "",
                editDisplayName = method.displayName ?: ""
            )
        }
    }

    fun closeEditSheet() {
        _state.update { it.copy(editingMethod = null) }
    }

    fun onEditNumberChanged(value: String) {
        _state.update { it.copy(editNumber = value) }
    }

    fun onEditDisplayNameChanged(value: String) {
        _state.update { it.copy(editDisplayName = value) }
    }

    fun saveMethodEdit() {
        val method = _state.value.editingMethod ?: return
        val number      = _state.value.editNumber.trim()
        val displayName = _state.value.editDisplayName.trim()

        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching {
                api.updateMethod(
                    "Bearer $token", method.id,
                    UpdateMethodRequest(
                        number      = number.ifEmpty { null },
                        displayName = displayName.ifEmpty { null }
                    )
                )
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    res.body()?.data?.let { newData ->
                        saveMethodsToCache(newData)
                        _state.update { current ->
                            current.copy(
                                methods = newData,
                                editingMethod = null,
                                successMessage = "আপডেট সফল হয়েছে ✓"
                            )
                        }
                    } ?: run {
                        _state.update { current ->
                            val updated = current.methods.map {
                                if (it.id == method.id)
                                    it.copy(number = number.ifEmpty { null },
                                            displayName = displayName.ifEmpty { null })
                                else it
                            }
                            saveMethodsToCache(updated)
                            current.copy(
                                methods = updated,
                                editingMethod = null,
                                successMessage = "আপডেট সফল হয়েছে ✓"
                            )
                        }
                    }
                    viewModelScope.launch {
                        delay(2000)
                        _state.update { it.copy(successMessage = null) }
                    }
                }
            }
        }
    }

    fun setSubTab(index: Int) {
        _state.update { it.copy(selectedSubTab = index, errorMessage = null) }
        if (index == 1) {
            loadChildDevices()
        }
    }

    fun loadChildDevices() {
        viewModelScope.launch {
            _state.update { it.copy(isChildDevicesLoading = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching { api.getChildDevices("Bearer $token") }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val devices = res.body()!!.data
                        _state.update { it.copy(childDevices = devices, isChildDevicesLoading = false) }
                    } else {
                        _state.update { it.copy(isChildDevicesLoading = false, errorMessage = "চাইল্ড ডিভাইস লোড ব্যর্থ হয়েছে") }
                    }
                }
                .onFailure { exception ->
                    _state.update { it.copy(isChildDevicesLoading = false, errorMessage = "নেটওয়ার্ক সমস্যা: ${exception.message}") }
                }
        }
    }

    fun openRemoteDeviceSettings(device: ChildDeviceDto) {
        _state.update {
            it.copy(
                activeRemoteDevice = device,
                remoteDeviceEditName = device.customDeviceName,
                remoteDeviceEditSim1Number = device.simOneNumber ?: "",
                remoteDeviceEditSim1Active = device.simOneActive == 1,
                remoteDeviceEditSim2Number = device.simTwoNumber ?: "",
                remoteDeviceEditSim2Active = device.simTwoActive == 1,
                remoteDeviceEditAppActive = device.isAppActive == 1,
                remoteDeviceEditPin = device.deviceSpecificPin ?: ""
            )
        }
    }

    fun closeRemoteDeviceSettings() {
        _state.update { it.copy(activeRemoteDevice = null) }
    }

    fun onRemoteDeviceEditNameChanged(name: String) {
        _state.update { it.copy(remoteDeviceEditName = name) }
    }

    fun onRemoteDeviceEditSim1NumberChanged(num: String) {
        _state.update { it.copy(remoteDeviceEditSim1Number = num) }
    }

    fun onRemoteDeviceEditSim1ActiveChanged(active: Boolean) {
        _state.update { it.copy(remoteDeviceEditSim1Active = active) }
    }

    fun onRemoteDeviceEditSim2NumberChanged(num: String) {
        _state.update { it.copy(remoteDeviceEditSim2Number = num) }
    }

    fun onRemoteDeviceEditSim2ActiveChanged(active: Boolean) {
        _state.update { it.copy(remoteDeviceEditSim2Active = active) }
    }

    fun onRemoteDeviceEditAppActiveChanged(active: Boolean) {
        _state.update { it.copy(remoteDeviceEditAppActive = active) }
    }

    fun onRemoteDeviceEditPinChanged(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _state.update { it.copy(remoteDeviceEditPin = pin) }
        }
    }

    fun saveRemoteDeviceSettings() {
        val device = _state.value.activeRemoteDevice ?: return
        val name = _state.value.remoteDeviceEditName.trim()
        val sim1Num = _state.value.remoteDeviceEditSim1Number.trim()
        val sim1Active = if (_state.value.remoteDeviceEditSim1Active) 1 else 0
        val sim2Num = _state.value.remoteDeviceEditSim2Number.trim()
        val sim2Active = if (_state.value.remoteDeviceEditSim2Active) 1 else 0
        val appActive = if (_state.value.remoteDeviceEditAppActive) 1 else 0
        val pin = _state.value.remoteDeviceEditPin.trim()

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            val request = RemoteUpdateDeviceRequest(
                deviceId = device.deviceId,
                customDeviceName = name,
                simOneNumber = sim1Num.ifEmpty { null },
                simOneActive = sim1Active,
                simTwoNumber = sim2Num.ifEmpty { null },
                simTwoActive = sim2Active,
                isAppActive = appActive,
                deviceSpecificPin = pin.ifEmpty { null }
            )

            runCatching { api.remoteUpdateDevice("Bearer $token", request) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                activeRemoteDevice = null,
                                successMessage = "চাইল্ড ডিভাইস আপডেট সফল হয়েছে ✓"
                            )
                        }
                        loadChildDevices()
                        viewModelScope.launch {
                            delay(2000)
                            _state.update { it.copy(successMessage = null) }
                        }
                    } else {
                        _state.update { it.copy(isSaving = false, errorMessage = "আপডেট ব্যর্থ হয়েছে") }
                    }
                }
                .onFailure { exception ->
                    _state.update { it.copy(isSaving = false, errorMessage = "নেটওয়ার্ক সমস্যা: ${exception.message}") }
                }
        }
    }

    private fun getToken(): String? {
        val token = SecurePreferences.decrypt(getApplication(), AppConfig.KEY_AUTH_TOKEN)
        return token.ifEmpty { null }
    }

    fun initiateRoleToggle(device: ChildDeviceDto, targetRole: String) {
        _state.update {
            it.copy(
                deviceForRoleToggle = device,
                targetRoleToggleValue = targetRole,
                showRolePinDialog = true,
                rolePinInput = "",
                rolePinError = null
            )
        }
    }

    fun onRolePinInputChanged(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _state.update { it.copy(rolePinInput = pin) }
        }
    }

    fun dismissRolePinDialog() {
        _state.update {
            it.copy(
                showRolePinDialog = false,
                deviceForRoleToggle = null,
                targetRoleToggleValue = "",
                rolePinInput = "",
                rolePinError = null
            )
        }
    }

    fun submitRoleToggle() {
        val device = _state.value.deviceForRoleToggle ?: return
        val role = _state.value.targetRoleToggleValue
        val pin = _state.value.rolePinInput

        if (pin.length < 4) {
            _state.update { it.copy(rolePinError = "কমপক্ষে ৪ ডিজিটের পিন দিন") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            val request = ToggleRemoteRoleRequest(
                remoteDeviceId = device.deviceId,
                newRole = role,
                pin = pin
            )

            runCatching { api.toggleRemoteRole("Bearer $token", request) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                successMessage = "রোল সফলভাবে পরিবর্তন হয়েছে ✓"
                            )
                        }
                        dismissRolePinDialog()
                        // Update activeRemoteDevice role locally if it's currently open in bottom sheet
                        _state.update { current ->
                            val currentActive = current.activeRemoteDevice
                            if (currentActive != null && currentActive.deviceId == device.deviceId) {
                                current.copy(activeRemoteDevice = currentActive.copy(deviceRole = role))
                            } else current
                        }
                        loadChildDevices()
                        viewModelScope.launch {
                            delay(2000)
                            _state.update { it.copy(successMessage = null) }
                        }
                    } else {
                        val err = if (res.code() == 401) "ভুল পিন কোড, অনুগ্রহ করে আবার চেষ্টা করুন।"
                                  else "রোল পরিবর্তন ব্যর্থ হয়েছে (${res.code()})"
                        _state.update { it.copy(isSaving = false, rolePinError = err) }
                    }
                }
                .onFailure { exception ->
                    _state.update { it.copy(isSaving = false, rolePinError = "নেটওয়ার্ক সমস্যা: ${exception.message}") }
                }
        }
    }

    fun autoDetectSimNumbers() {
        viewModelScope.launch {
            delay(500L) // Small delay to let OS permission state sync
            val context = getApplication<Application>().applicationContext
            val (sim1Num, sim2Num) = DeviceIdHelper.getSimNumbers(context)
            if (!sim1Num.isNullOrBlank() && _state.value.sim1Number.isBlank()) {
                onSimNumberChanged(1, sim1Num)
            }
            if (!sim2Num.isNullOrBlank() && _state.value.sim2Number.isBlank()) {
                onSimNumberChanged(2, sim2Num)
            }
        }
    }

    private var sim1NumberDebounceJob: Job? = null
    private var sim2NumberDebounceJob: Job? = null

    fun onSimNumberChanged(simSlot: Int, num: String) {
        val cleanNum = num.take(11).filter { it.isDigit() }
        _state.update {
            if (simSlot == 1) it.copy(sim1Number = cleanNum)
            else it.copy(sim2Number = cleanNum)
        }

        if (simSlot == 1) {
            sim1NumberDebounceJob?.cancel()
            sim1NumberDebounceJob = viewModelScope.launch {
                delay(1500)
                saveSimNumberToServer(1, cleanNum)
            }
        } else {
            sim2NumberDebounceJob?.cancel()
            sim2NumberDebounceJob = viewModelScope.launch {
                delay(1500)
                saveSimNumberToServer(2, cleanNum)
            }
        }
    }

    private suspend fun saveSimNumberToServer(simSlot: Int, number: String) {
        val token = getToken() ?: return
        val methodsToUpdate = _state.value.methods.filter { it.simSlot == simSlot }
        
        methodsToUpdate.forEach { method ->
            runCatching {
                api.updateMethod(
                    "Bearer $token",
                    method.id,
                    UpdateMethodRequest(number = number, displayName = method.displayName)
                )
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    res.body()?.data?.let { newData ->
                        saveMethodsToCache(newData)
                        _state.update { it.copy(methods = newData) }
                    } ?: run {
                        _state.update { current ->
                            val updated = current.methods.map {
                                if (it.id == method.id) it.copy(number = number) else it
                            }
                            saveMethodsToCache(updated)
                            current.copy(methods = updated)
                        }
                    }
                }
            }
        }
        validateAndSyncSimToggles()
    }

    fun validateAndSyncSimToggles() {
        val stateVal = _state.value
        val sim1Num = stateVal.sim1Number
        val sim2Num = stateVal.sim2Number
        val methods = stateVal.methods

        val hasActiveMethodSim1 = methods.any { it.simSlot == 1 && it.isEnabled == 1 }
        val hasActiveMethodSim2 = methods.any { it.simSlot == 2 && it.isEnabled == 1 }

        val isSim1Valid = sim1Num.length == 11 && hasActiveMethodSim1
        val isSim2Valid = sim2Num.length == 11 && hasActiveMethodSim2

        val newSim1Enabled = if (isSim1Valid) stateVal.sim1Enabled else false
        val newSim2Enabled = if (isSim2Valid) stateVal.sim2Enabled else false

        if (newSim1Enabled != stateVal.sim1Enabled || newSim2Enabled != stateVal.sim2Enabled) {
            prefs.edit().apply {
                putBoolean(AppConfig.KEY_SIM1_ENABLED, newSim1Enabled)
                putBoolean(AppConfig.KEY_SIM2_ENABLED, newSim2Enabled)
                apply()
            }
            _state.update {
                it.copy(
                    sim1Enabled = newSim1Enabled,
                    sim2Enabled = newSim2Enabled
                )
            }
        }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _state.update { it.copy(isTemplatesLoading = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            runCatching { api.getTemplates("Bearer $token") }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val list = res.body()!!.templates
                        _state.update { it.copy(templates = list, isTemplatesLoading = false) }
                        performDropSync()
                    } else {
                        _state.update { it.copy(isTemplatesLoading = false) }
                    }
                }
                .onFailure {
                    _state.update { it.copy(isTemplatesLoading = false) }
                }
        }
    }

    fun toggleTemplate(simSlot: Int, template: SmsTemplateDto) {
        val existingMethod = _state.value.methods.find { it.simSlot == simSlot && it.templateId == template.id }
        if (existingMethod != null) {
            toggleMethod(existingMethod)
        } else {
            viewModelScope.launch {
                _state.update { it.copy(isSaving = true) }
                val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
                val num = if (simSlot == 1) _state.value.sim1Number else _state.value.sim2Number
                val request = AddGatewayMethodRequest(
                    simSlot = simSlot,
                    provider = template.templateName,
                    templateId = template.id,
                    number = num.ifEmpty { null }
                )

                runCatching { api.addGatewayMethod("Bearer $token", request) }
                    .onSuccess { res ->
                        _state.update { it.copy(isSaving = false) }
                        if (res.isSuccessful && res.body()?.success == true) {
                            res.body()?.data?.let { newData ->
                                saveMethodsToCache(newData)
                                _state.update { it.copy(methods = newData) }
                            }
                            loadGatewayMethods()
                        } else {
                            setError("মেথড যোগ করতে ব্যর্থ হয়েছে (${res.code()})")
                        }
                    }
                    .onFailure {
                        _state.update { it.copy(isSaving = false) }
                        setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                    }
            }
        }
    }





    private fun setError(msg: String) {
        _state.update { it.copy(isLoading = false, isSaving = false, errorMessage = msg) }
    }

    private fun performDropSync() {
        val currentTemplates = _state.value.templates
        val currentMethods = _state.value.methods
        
        // Skip check if templates are still loading or empty (unless loading has finished)
        if (currentTemplates.isEmpty() && _state.value.isTemplatesLoading) return
        
        val activeTemplateIds = currentTemplates.mapNotNull { it.id }.toSet()
        
        val methodsToDrop = currentMethods.filter { method ->
            method.templateId != null && !activeTemplateIds.contains(method.templateId)
        }
        
        if (methodsToDrop.isNotEmpty()) {
            val updatedMethods = currentMethods.filterNot { it in methodsToDrop }
            _state.update { it.copy(methods = updatedMethods) }
            saveMethodsToCache(updatedMethods)
            validateAndSyncSimToggles()
            
            // Notify backend about deactivation of these dropped methods
            val token = getToken()
            if (token != null) {
                viewModelScope.launch {
                    methodsToDrop.forEach { method ->
                        if (method.isEnabled == 1) {
                            runCatching {
                                api.toggleMethod("Bearer $token", method.id, ToggleRequest(0))
                            }
                        }
                    }
                }
            }
        }
    }
}
