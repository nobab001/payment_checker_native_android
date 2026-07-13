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
import online.paychek.app.services.connectivity.ConnectionEngine
import online.paychek.app.services.sync.NumberHeartbeatEngine
import online.paychek.app.utils.DeviceIdHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    val remoteGatewayMethods: List<GatewayMethod> = emptyList(),
    val remoteTemplates: List<SmsTemplateDto> = emptyList(),
    val isRemoteGatewayLoading: Boolean       = false,
    
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
    val isTemplatesLoading: Boolean           = false,
    val showPremiumUpgradeDialog: Boolean     = false,
    val dialogErrorMessage: String?           = null,
    val hasCustomSenderPermission: Boolean    = false,
    val hasTemplatePermission: Boolean        = false,
    val hasDevicePermission: Boolean          = true,
    val showPermissionDialog: Boolean         = false,
    val permissionDialogMessage: String?      = null,
    val pendingSimConflict: SimConflictUi?      = null,

    // Account-wide active numbers modal
    val showAccountNumbersSheet: Boolean      = false,
    val accountNumbers: List<AccountNumberDto> = emptyList(),
    val isAccountNumbersLoading: Boolean        = false,
    val accountNumberToDelete: AccountNumberDto? = null,
    val isDeletingAccountNumber: Boolean        = false,

    // Delete device flow
    val showDeleteDevicePinDialog: Boolean      = false,
    val deviceToDelete: ChildDeviceDto?         = null,
    val deleteDevicePinInput: String              = "",
    val deleteDevicePinError: String?             = null,
    val isDeletingDevice: Boolean               = false
)

data class SimConflictUi(
    val simSlot: Int,
    val phoneNumber: String,
    val runningDeviceName: String
)

// =============================================================================
// ViewModel
// =============================================================================
class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val api   = RetrofitClient.gatewayApiService
    private val prefs = application.getSharedPreferences(AppConfig.PREF_NAME, Context.MODE_PRIVATE)
    private val connectionEngine = ConnectionEngine.getInstance(application)

    val connectionBanner = connectionEngine.banner
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val hasInternet = connectionEngine.status
        .map { it.hasInternet }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private var saveJob: Job? = null
    private val lastConfirmedLookupNumber = mutableMapOf(1 to "", 2 to "")

    init {
        val sim1 = prefs.getBoolean(AppConfig.KEY_SIM1_ENABLED, true)
        val sim2 = prefs.getBoolean(AppConfig.KEY_SIM2_ENABLED, true)
        
        // Load cached methods to show offline/instantly
        val cachedJson = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsCache(application)
        val cachedList = if (cachedJson.isNotEmpty() && cachedJson != "[]") {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<GatewayMethod>>() {}.type
                online.paychek.app.utils.GsonUtils.gson.fromJson<List<GatewayMethod>>(cachedJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        val cachedTemplatesJson = online.paychek.app.data.local.prefs.PrefsHelper.getSmsTemplatesCache(application)
        val cachedTemplatesList = if (cachedTemplatesJson.isNotEmpty() && cachedTemplatesJson != "[]") {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<SmsTemplateDto>>() {}.type
                online.paychek.app.utils.GsonUtils.gson.fromJson<List<SmsTemplateDto>>(cachedTemplatesJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        val cachedSim1Num = cachedList.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number ?: ""
        val cachedSim2Num = cachedList.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number ?: ""
        val cachedEnt = online.paychek.app.utils.AccountEntitlementsStore.readCached(application)

        RetrofitClient.init(application)
        connectionEngine.startMonitoring(viewModelScope)

        _state.update {
            it.copy(
                sim1Enabled = sim1, 
                sim2Enabled = sim2, 
                methods = cachedList,
                templates = cachedTemplatesList,
                sim1Number = cachedSim1Num,
                sim2Number = cachedSim2Num,
                isLoading = cachedList.isEmpty(),
                hasCustomSenderPermission = cachedEnt.hasCustomSender,
                hasTemplatePermission = cachedEnt.hasTemplate,
                hasDevicePermission = cachedEnt.hasDevice
            ) 
        }
        lastConfirmedLookupNumber[1] = cachedSim1Num
        lastConfirmedLookupNumber[2] = cachedSim2Num

        loadAccountEntitlements()

        viewModelScope.launch {
            connectionEngine.status
                .map { it.hasInternet }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    loadGatewayMethods()
                    loadTemplates()
                    if (_state.value.selectedSubTab == 1) {
                        loadChildDevices()
                    }
                }
        }
    }

    private fun saveMethodsToCache(methods: List<GatewayMethod>) {
        try {
            val json = online.paychek.app.utils.GsonUtils.gson.toJson(methods)
            val ok = online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsCache(getApplication(), json)
            if (!ok) {
                android.util.Log.e("DeviceViewModel", "Gateway methods cache save/verify failed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Drop all cached gateway methods for one SIM slot (before loading a new number profile). */
    private fun clearSlotLocalCache(simSlot: Int) {
        _state.update { current ->
            val updated = current.methods.filter { it.simSlot != simSlot }
            saveMethodsToCache(updated)
            current.copy(methods = updated)
        }
    }

    private fun applyServerMethods(serverData: List<GatewayMethod>) {
        saveMethodsToCache(serverData)
        val sim1Num = serverData.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number
            ?: _state.value.sim1Number
        val sim2Num = serverData.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number
            ?: _state.value.sim2Number
        _state.update {
            it.copy(methods = serverData, sim1Number = sim1Num, sim2Number = sim2Num)
        }
    }

    fun loadGatewayMethods() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            val lastSync = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsLastSync(getApplication())

            runCatching { api.getGatewayMethods("Bearer $token", lastSync) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val body = res.body()!!
                        body.dataVersion?.takeIf { it > 0 }?.let {
                            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(getApplication(), it)
                        }

                        if (body.unchanged == true || body.data == null) {
                            // Server says unchanged — if local cache empty, force full refetch
                            val localEmpty = !online.paychek.app.data.local.prefs.PrefsHelper
                                .hasGatewayMethodsCache(getApplication()) && _state.value.methods.isEmpty()
                            if (localEmpty) {
                                online.paychek.app.data.local.prefs.PrefsHelper
                                    .setGatewayMethodsLastSync(getApplication(), 0L)
                                runCatching { api.getGatewayMethods("Bearer $token", 0L) }
                                    .onSuccess { fullRes ->
                                        val fullBody = fullRes.body()
                                        if (fullRes.isSuccessful && fullBody?.success == true && fullBody.data != null) {
                                            fullBody.dataVersion?.takeIf { it > 0 }?.let {
                                                online.paychek.app.data.local.prefs.PrefsHelper
                                                    .setGatewayMethodsLastSync(getApplication(), it)
                                            }
                                            val sorted = fullBody.data.sortedBy { it.priority }
                                            val sim1Num = sorted.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number
                                                ?: _state.value.sim1Number
                                            val sim2Num = sorted.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number
                                                ?: _state.value.sim2Number
                                            lastConfirmedLookupNumber[1] = sim1Num
                                            lastConfirmedLookupNumber[2] = sim2Num
                                            _state.update {
                                                it.copy(
                                                    methods = sorted,
                                                    sim1Number = sim1Num,
                                                    sim2Number = sim2Num,
                                                    isLoading = false
                                                )
                                            }
                                            saveMethodsToCache(sorted)
                                        } else {
                                            _state.update { it.copy(isLoading = false) }
                                        }
                                    }
                                    .onFailure { _state.update { it.copy(isLoading = false) } }
                            } else {
                                _state.update { it.copy(isLoading = false) }
                            }
                            validateAndSyncSimToggles()
                            performDropSync()
                            return@onSuccess
                        }

                        val sorted = body.data.sortedBy { it.priority }
                        val sim1Num = sorted.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number ?: _state.value.sim1Number
                        val sim2Num = sorted.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number ?: _state.value.sim2Number
                        lastConfirmedLookupNumber[1] = sim1Num
                        lastConfirmedLookupNumber[2] = sim2Num
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

        if (!isSimSlotActive(method.simSlot)) return

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
        val stateVal = _state.value
        val isCurrentlyEnabled = if (simSlot == 1) stateVal.sim1Enabled else stateVal.sim2Enabled
        val newValue = !isCurrentlyEnabled
        val phoneNumber = if (simSlot == 1) stateVal.sim1Number else stateVal.sim2Number

        _state.update {
            if (simSlot == 1) {
                prefs.edit().putBoolean(AppConfig.KEY_SIM1_ENABLED, newValue).apply()
                it.copy(sim1Enabled = newValue)
            } else {
                prefs.edit().putBoolean(AppConfig.KEY_SIM2_ENABLED, newValue).apply()
                it.copy(sim2Enabled = newValue)
            }
        }

        if (phoneNumber.length != 11) return

        viewModelScope.launch {
            isSimToggleInFlight = true
            try {
                val token = getToken() ?: return@launch
                if (newValue) {
                    // Lookup লোকাল সিলেক্টেড টেমপ্লেট মুছে ফেলতে পারে — আগে স্ন্যাপশট রাখি।
                    val preservedItems = snapshotEnabledSlotBulkItems(simSlot)

                    val lookupOk = performSlotLookup(
                        simSlot = simSlot,
                        cleanNum = phoneNumber,
                        wipeLocalSlot = false,
                        skipValidate = true
                    )
                    if (!lookupOk) {
                        if (_state.value.pendingSimConflict == null) {
                            revertSimToggle(simSlot)
                        }
                        return@launch
                    }

                    val bulkItems = mergeSlotBulkItems(simSlot, preservedItems)
                    if (bulkItems.isEmpty()) {
                        revertSimToggle(simSlot)
                        setError("সিম চালু করতে আগে অন্তত একটি টেমপ্লেট সিলেক্ট করুন")
                        return@launch
                    }

                    runCatching {
                        api.bulkSyncSlotMethods(
                            "Bearer $token",
                            BulkSyncRequest(
                                simSlot = simSlot,
                                phoneNumber = phoneNumber,
                                methods = bulkItems,
                                replaceSlot = true,
                                activateBinding = true
                            )
                        )
                    }.onSuccess { res ->
                        if (!res.isSuccessful) {
                            revertSimToggle(simSlot)
                            setError("সিম সক্রিয় করতে ব্যর্থ হয়েছে (${res.code()})")
                            return@onSuccess
                        }
                        val body = res.body()
                        if (body?.hasConflict == true) {
                            revertSimToggle(simSlot)
                            _state.update {
                                it.copy(
                                    pendingSimConflict = SimConflictUi(
                                        simSlot = simSlot,
                                        phoneNumber = phoneNumber,
                                        runningDeviceName = body.runningDeviceName ?: "অন্য ডিভাইস"
                                    )
                                )
                            }
                            return@onSuccess
                        }
                        if (body?.success == true) {
                            body.data?.let { newData ->
                                saveMethodsToCache(newData)
                                _state.update { it.copy(methods = newData) }
                            }
                            enableSimSlot(simSlot)
                            reactivatedSlotsThisSession.add(simSlot)
                        } else {
                            revertSimToggle(simSlot)
                            setError(body?.message ?: "সিম সক্রিয় করতে ব্যর্থ হয়েছে")
                        }
                    }.onFailure {
                        revertSimToggle(simSlot)
                        setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                    }
                } else {
                    runCatching {
                        api.setSlotActive(
                            "Bearer $token",
                            SlotActiveRequest(
                                simSlot = simSlot,
                                phoneNumber = phoneNumber,
                                isActive = 0
                            )
                        )
                    }.onSuccess { res ->
                        if (res.isSuccessful) {
                            res.body()?.data?.let { newData ->
                                saveMethodsToCache(newData)
                                _state.update { it.copy(methods = newData) }
                            }
                            reactivatedSlotsThisSession.remove(simSlot)
                        }
                    }
                }
            } finally {
                isSimToggleInFlight = false
                if (_state.value.pendingSimConflict == null) {
                    validateAndSyncSimToggles()
                }
            }
        }
    }

    /** টগলের আগে ইউজার যে টেমপ্লেট চালু রেখেছে তার স্ন্যাপশট। */
    private fun snapshotEnabledSlotBulkItems(simSlot: Int): List<BulkSyncMethodItem> {
        return _state.value.methods
            .filter { it.simSlot == simSlot && it.isEnabled == 1 && it.provider.isNotBlank() }
            .map { method ->
                BulkSyncMethodItem(
                    templateId = method.templateId,
                    provider = method.provider,
                    isEnabled = 1
                )
            }
    }

    /**
     * Lookup/profile এর পর সার্ভার মেথড + ইউজারের প্রি-সিলেক্টেড টেমপ্লেট মার্জ।
     * স্লটে যেকোনো মেথড থাকলে (disabled হলেও) চালু করে bulk-sync-এ পাঠায় —
     * খালি replace_slot দিয়ে সব ডিলিট হওয়া বন্ধ করে।
     */
    private fun mergeSlotBulkItems(
        simSlot: Int,
        preserved: List<BulkSyncMethodItem>
    ): List<BulkSyncMethodItem> {
        val byKey = LinkedHashMap<String, BulkSyncMethodItem>()

        fun keyOf(templateId: Int?, provider: String): String =
            if (templateId != null) "t:$templateId" else "p:${provider.lowercase()}"

        fun putItem(item: BulkSyncMethodItem) {
            if (item.provider.isBlank()) return
            byKey[keyOf(item.templateId, item.provider)] = item.copy(isEnabled = 1)
        }

        preserved.forEach(::putItem)

        _state.value.methods
            .filter { it.simSlot == simSlot && it.provider.isNotBlank() }
            .forEach { method ->
                val wantOn = method.isEnabled == 1 ||
                    preserved.any { p ->
                        (p.templateId != null && p.templateId == method.templateId) ||
                            (p.templateId == null && p.provider.equals(method.provider, ignoreCase = true))
                    }
                if (wantOn) {
                    putItem(
                        BulkSyncMethodItem(
                            templateId = method.templateId,
                            provider = method.provider,
                            isEnabled = 1
                        )
                    )
                }
            }

        if (byKey.isEmpty()) {
            // প্রিজারভড খালি কিন্তু স্লটে মেথড আছে → সব চালু করে অ্যাক্টিভেট (পুরনো প্রোফাইল)
            _state.value.methods
                .filter { it.simSlot == simSlot && it.provider.isNotBlank() }
                .forEach { method ->
                    putItem(
                        BulkSyncMethodItem(
                            templateId = method.templateId,
                            provider = method.provider,
                            isEnabled = 1
                        )
                    )
                }
        }

        return byKey.values.toList()
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
        if (index == 1 && !_state.value.hasDevicePermission) {
            _state.update {
                it.copy(
                    showPermissionDialog = true,
                    permissionDialogMessage = "আপনার প্যাকেজে ডিভাইস ম্যানেজমেন্টের পারমিশন নেই।"
                )
            }
            return
        }
        _state.update { it.copy(selectedSubTab = index, errorMessage = null) }
        if (index == 1) {
            loadChildDevices()
        }
    }

    fun dismissPermissionDialog() {
        _state.update { it.copy(showPermissionDialog = false, permissionDialogMessage = null) }
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
                remoteDeviceEditPin = device.deviceSpecificPin ?: "",
                remoteGatewayMethods = emptyList(),
                remoteTemplates = emptyList()
            )
        }
        loadRemoteGatewayData()
    }

    fun closeRemoteDeviceSettings() {
        _state.update {
            it.copy(
                activeRemoteDevice = null,
                remoteGatewayMethods = emptyList(),
                remoteTemplates = emptyList(),
                isRemoteGatewayLoading = false
            )
        }
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
                                successMessage = "চাইল্ড ডিভাইস আপডেট সফল হয়েছে ✓",
                                activeRemoteDevice = device.copy(
                                    customDeviceName = name,
                                    simOneNumber = sim1Num.ifEmpty { null },
                                    simOneActive = sim1Active,
                                    simTwoNumber = sim2Num.ifEmpty { null },
                                    simTwoActive = sim2Active,
                                    isAppActive = appActive,
                                    deviceSpecificPin = pin.ifEmpty { null }
                                )
                            )
                        }
                        loadChildDevices()
                        loadRemoteGatewayData()
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

    private fun activeRemoteDeviceId(): String? = _state.value.activeRemoteDevice?.deviceId

    private fun remoteSimNumber(simSlot: Int): String {
        val s = _state.value
        return (if (simSlot == 1) s.remoteDeviceEditSim1Number else s.remoteDeviceEditSim2Number)
            .trim().filter { it.isDigit() }.take(11)
    }

    private fun isRemoteSimActive(simSlot: Int): Boolean {
        val s = _state.value
        return if (simSlot == 1) s.remoteDeviceEditSim1Active else s.remoteDeviceEditSim2Active
    }

    fun loadRemoteGatewayData() {
        val deviceId = activeRemoteDeviceId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isRemoteGatewayLoading = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            runCatching {
                val methodsRes = api.getGatewayMethods("Bearer $token", 0L, deviceId)
                val templatesRes = api.getTemplates("Bearer $token", 0L, deviceId)
                Pair(methodsRes, templatesRes)
            }.onSuccess { (methodsRes, templatesRes) ->
                val methods = if (methodsRes.isSuccessful && methodsRes.body()?.success == true) {
                    methodsRes.body()?.data ?: emptyList()
                } else emptyList()
                val templates = if (templatesRes.isSuccessful && templatesRes.body()?.success == true) {
                    templatesRes.body()?.templates ?: emptyList()
                } else emptyList()
                _state.update {
                    // সার্ভারের gateway_methods থেকে প্রতিটি slot-এর নাম্বার বের করে remote
                    // edit ফিল্ডে বসাই — যাতে "আদার্স ডিভাইস" সেটিংসে শুধু টেমপ্লেট নয়,
                    // নাম্বারও লোড হয়। সার্ভারে নাম্বার থাকলে সেটাই প্রাধান্য পায়; না থাকলে
                    // আগের (GET /v1/devices থেকে আসা) নাম্বার অপরিবর্তিত রাখি।
                    val serverSim1 = methods.firstOrNull { m -> m.simSlot == 1 && !m.number.isNullOrBlank() }?.number
                    val serverSim2 = methods.firstOrNull { m -> m.simSlot == 2 && !m.number.isNullOrBlank() }?.number
                    it.copy(
                        remoteGatewayMethods = methods,
                        remoteTemplates = templates,
                        remoteDeviceEditSim1Number = serverSim1 ?: it.remoteDeviceEditSim1Number,
                        remoteDeviceEditSim2Number = serverSim2 ?: it.remoteDeviceEditSim2Number,
                        isRemoteGatewayLoading = false
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isRemoteGatewayLoading = false,
                        errorMessage = "রিমোট গেটওয়ে লোড ব্যর্থ: ${e.message}"
                    )
                }
            }
        }
    }

    fun remoteToggleTemplate(simSlot: Int, template: SmsTemplateDto) {
        val deviceId = activeRemoteDeviceId() ?: return
        val existing = _state.value.remoteGatewayMethods.find {
            it.simSlot == simSlot && it.templateId == template.id
        }
        if (existing != null) {
            remoteToggleMethod(existing)
            return
        }
        if (!isRemoteSimActive(simSlot)) {
            setError("সিম স্লট চালু করুন, তারপর টেমপ্লেট সিলেক্ট করুন")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            val num = remoteSimNumber(simSlot)
            val request = AddGatewayMethodRequest(
                simSlot = simSlot,
                provider = template.templateName,
                templateId = template.id,
                number = num.ifEmpty { null }
            )
            runCatching { api.addGatewayMethod("Bearer $token", request, deviceId) }
                .onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        } ?: loadRemoteGatewayData()
                    } else {
                        setError("রিমোট টেমপ্লেট যোগ ব্যর্থ (${res.code()})")
                    }
                }
                .onFailure { setError("নেটওয়ার্ক সমস্যা: ${it.message}") }
        }
    }

    fun remoteToggleMethod(method: GatewayMethod) {
        val deviceId = activeRemoteDeviceId() ?: return
        val newEnabled = if (method.isEnabled == 1) 0 else 1
        _state.update { current ->
            val updated = current.remoteGatewayMethods.map {
                if (it.id == method.id) it.copy(isEnabled = newEnabled) else it
            }
            current.copy(remoteGatewayMethods = updated)
        }
        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching { api.toggleMethod("Bearer $token", method.id, ToggleRequest(newEnabled), deviceId) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        }
                    } else {
                        loadRemoteGatewayData()
                        setError("রিমোট টগল ব্যর্থ (${res.code()})")
                    }
                }
                .onFailure {
                    loadRemoteGatewayData()
                    setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                }
        }
    }

    fun remoteToggleSim(simSlot: Int, active: Boolean) {
        val deviceId = activeRemoteDeviceId() ?: return
        _state.update {
            if (simSlot == 1) it.copy(remoteDeviceEditSim1Active = active)
            else it.copy(remoteDeviceEditSim2Active = active)
        }
        val phoneNumber = remoteSimNumber(simSlot)
        if (phoneNumber.length != 11) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            if (active) {
                val slotMethods = _state.value.remoteGatewayMethods.filter {
                    it.simSlot == simSlot && it.isEnabled == 1
                }
                val bulkItems = slotMethods.map { method ->
                    BulkSyncMethodItem(
                        templateId = method.templateId,
                        provider = method.provider,
                        isEnabled = method.isEnabled
                    )
                }
                runCatching {
                    api.bulkSyncSlotMethods(
                        "Bearer $token",
                        BulkSyncRequest(
                            simSlot = simSlot,
                            phoneNumber = phoneNumber,
                            methods = bulkItems,
                            replaceSlot = true,
                            activateBinding = true
                        ),
                        deviceId
                    )
                }.onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        }
                    } else {
                        _state.update {
                            if (simSlot == 1) it.copy(remoteDeviceEditSim1Active = false)
                            else it.copy(remoteDeviceEditSim2Active = false)
                        }
                        setError("রিমোট সিম সক্রিয় ব্যর্থ")
                    }
                }.onFailure { e ->
                    _state.update {
                        if (simSlot == 1) it.copy(remoteDeviceEditSim1Active = false)
                        else it.copy(remoteDeviceEditSim2Active = false)
                    }
                    setError("নেটওয়ার্ক সমস্যা: ${e.message}")
                }
            } else {
                runCatching {
                    api.setSlotActive(
                        "Bearer $token",
                        SlotActiveRequest(simSlot = simSlot, phoneNumber = phoneNumber, isActive = 0),
                        deviceId
                    )
                }.onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        }
                    }
                }.onFailure { e ->
                    _state.update { it.copy(isSaving = false) }
                    setError("নেটওয়ার্ক সমস্যা: ${e.message}")
                }
            }
        }
    }

    fun remoteAddCustomSender(
        simSlot: Int,
        senderId: String,
        officialTemplateId: Int? = null,
        createPersonal: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val deviceId = activeRemoteDeviceId() ?: return
        if (!_state.value.hasCustomSenderPermission) {
            _state.update { it.copy(showPremiumUpgradeDialog = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, dialogErrorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            val request = AddCustomSenderRequest(
                simSlot = simSlot,
                senderId = senderId.trim(),
                deviceId = deviceId,
                officialTemplateId = officialTemplateId,
                createPersonal = if (createPersonal) true else null
            )
            runCatching { api.addCustomSender("Bearer $token", request, deviceId) }
                .onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        } ?: loadRemoteGatewayData()
                        onSuccess()
                    } else if (res.code() == 403) {
                        _state.update { it.copy(showPremiumUpgradeDialog = true) }
                    } else {
                        val msg = online.paychek.app.utils.ApiErrorParser.parse(res.errorBody()?.string())
                            ?: "রিমোট কাস্টম সেন্ডার যোগ ব্যর্থ (${res.code()})"
                        _state.update { it.copy(dialogErrorMessage = msg) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, dialogErrorMessage = "নেটওয়ার্ক সমস্যা: ${e.message}") }
                }
        }
    }

    fun remoteDeleteCustomSender(methodId: Int) {
        val deviceId = activeRemoteDeviceId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            runCatching { api.deleteGatewayMethod("Bearer $token", methodId, deviceId) }
                .onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            _state.update { it.copy(remoteGatewayMethods = newData) }
                        } ?: loadRemoteGatewayData()
                    } else {
                        setError("রিমোট সেন্ডার মুছতে ব্যর্থ (${res.code()})")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isSaving = false) }
                    setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                }
        }
    }

    fun remoteUpdateSimNumber(simSlot: Int, rawNumber: String) {
        val digits = rawNumber.filter { it.isDigit() }.take(11)
        _state.update {
            if (simSlot == 1) it.copy(remoteDeviceEditSim1Number = digits)
            else it.copy(remoteDeviceEditSim2Number = digits)
        }
        if (digits.length != 11) return
        val deviceId = activeRemoteDeviceId() ?: return
        viewModelScope.launch {
            val token = getToken() ?: return@launch
            runCatching {
                api.lookupSlotNumber(
                    "Bearer $token",
                    SlotLookupRequest(simSlot = simSlot, phoneNumber = digits),
                    deviceId
                )
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    val methods = res.body()?.cachedMethods ?: res.body()?.data
                    methods?.let { list ->
                        _state.update { it.copy(remoteGatewayMethods = list) }
                    } ?: loadRemoteGatewayData()
                }
            }
        }
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

    fun initiateDeleteDevice(device: ChildDeviceDto) {
        _state.update {
            it.copy(
                showDeleteDevicePinDialog = true,
                deviceToDelete = device,
                deleteDevicePinInput = "",
                deleteDevicePinError = null
            )
        }
    }

    fun dismissDeleteDeviceDialog() {
        _state.update {
            it.copy(
                showDeleteDevicePinDialog = false,
                deviceToDelete = null,
                deleteDevicePinInput = "",
                deleteDevicePinError = null,
                isDeletingDevice = false
            )
        }
    }

    fun onDeleteDevicePinChanged(pin: String) {
        if (pin.length <= 6 && pin.all { it.isDigit() }) {
            _state.update { it.copy(deleteDevicePinInput = pin) }
        }
    }

    fun confirmDeleteDevice() {
        val device = _state.value.deviceToDelete ?: return
        val pin = _state.value.deleteDevicePinInput
        if (pin.length < 4) {
            _state.update { it.copy(deleteDevicePinError = "কমপক্ষে ৪ ডিজিটের পিন দিন") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isDeletingDevice = true, deleteDevicePinError = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching {
                api.deleteDevice(
                    "Bearer $token",
                    DeleteDeviceRequest(deviceId = device.deviceId, pin = pin)
                )
            }.onSuccess { res ->
                if (res.isSuccessful && res.body()?.success == true) {
                    dismissDeleteDeviceDialog()
                    if (_state.value.activeRemoteDevice?.deviceId == device.deviceId) {
                        closeRemoteDeviceSettings()
                    }
                    _state.update { it.copy(successMessage = "ডিভাইস মুছে ফেলা হয়েছে ✓") }
                    loadChildDevices()
                    viewModelScope.launch {
                        delay(2000)
                        _state.update { it.copy(successMessage = null) }
                    }
                } else {
                    val err = if (res.code() == 400 || res.code() == 401) {
                        "ভুল পিন কোড, অনুগ্রহ করে আবার চেষ্টা করুন।"
                    } else {
                        "ডিভাইস মুছতে ব্যর্থ (${res.code()})"
                    }
                    _state.update { it.copy(isDeletingDevice = false, deleteDevicePinError = err) }
                }
            }.onFailure { exception ->
                _state.update {
                    it.copy(
                        isDeletingDevice = false,
                        deleteDevicePinError = "নেটওয়ার্ক সমস্যা: ${exception.message}"
                    )
                }
            }
        }
    }

    private var sim1NumberDebounceJob: Job? = null
    private var sim2NumberDebounceJob: Job? = null
    private var isApplyingProfile = false
    // লগইন/রিস্টোরে toggle লোকালি ON থাকলেও ব্যাকএন্ডে is_active সেট নাও থাকতে পারে —
    // সেশন প্রতি একবার valid+ON slot গুলো সার্ভারে re-activate করা হয়। মাঝপথে ডুপ্লিকেট চলা এড়াতে guard।
    private var isReactivatingSlots = false
    private val reactivatedSlotsThisSession = mutableSetOf<Int>()
    /** SIM toggle API চলাকালীন validate যেন জোর করে OFF না করে (ON→OFF churn)। */
    private var isSimToggleInFlight = false

    private fun isSimSlotActive(simSlot: Int): Boolean {
        return if (simSlot == 1) _state.value.sim1Enabled else _state.value.sim2Enabled
    }

    private fun revertSimToggle(simSlot: Int) {
        prefs.edit().apply {
            if (simSlot == 1) putBoolean(AppConfig.KEY_SIM1_ENABLED, false)
            else putBoolean(AppConfig.KEY_SIM2_ENABLED, false)
            apply()
        }
        _state.update {
            if (simSlot == 1) it.copy(sim1Enabled = false) else it.copy(sim2Enabled = false)
        }
    }

    private fun enableSimSlot(simSlot: Int) {
        prefs.edit().apply {
            if (simSlot == 1) putBoolean(AppConfig.KEY_SIM1_ENABLED, true)
            else putBoolean(AppConfig.KEY_SIM2_ENABLED, true)
            apply()
        }
        _state.update {
            if (simSlot == 1) it.copy(sim1Enabled = true) else it.copy(sim2Enabled = true)
        }
    }

    /** After physical SIM swap, refresh slot numbers from the device when they changed. */
    fun syncPhysicalSimNumbers() {
        viewModelScope.launch {
            delay(300L)
            val context = getApplication<Application>().applicationContext
            val (physicalSim1, physicalSim2) = DeviceIdHelper.getSimNumbers(context)
            val stateVal = _state.value

            physicalSim1?.takeIf { it.length == 11 && it != stateVal.sim1Number }?.let { num ->
                onSimNumberChanged(1, num)
            }
            physicalSim2?.takeIf { it.length == 11 && it != stateVal.sim2Number }?.let { num ->
                onSimNumberChanged(2, num)
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
            syncPhysicalSimNumbers()
        }
    }

    private fun resolvePhysicalNumber(simSlot: Int, typed: String): String {
        val context = getApplication<Application>().applicationContext
        val hasPhoneState = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            )
        if (!hasPhoneState) return typed

        val (sim1, sim2) = DeviceIdHelper.getSimNumbers(context)
        val physical = (if (simSlot == 1) sim1 else sim2)
            ?.filter { it.isDigit() }
            ?.take(11)
            .orEmpty()
        return if (physical.length == 11) physical else typed
    }

    private fun nextLocalMethodId(): Int {
        val minId = _state.value.methods.minOfOrNull { it.id } ?: 0
        return if (minId > 0) -1 else minId - 1
    }

    private fun addLocalTemplateMethod(simSlot: Int, template: SmsTemplateDto) {
        val num = if (simSlot == 1) _state.value.sim1Number else _state.value.sim2Number
        val maxPriority = _state.value.methods.maxOfOrNull { it.priority } ?: 0
        val newMethod = GatewayMethod(
            id = nextLocalMethodId(),
            simSlot = simSlot,
            provider = template.templateName,
            number = num.ifEmpty { null },
            displayName = null,
            isEnabled = 1,
            priority = maxPriority + 1,
            templateId = template.id,
            senderId = template.senderId,
            senderNumber = template.senderNumber,
            matchingKeyword = template.matchingKeyword,
            regexPattern = template.regexPattern,
            customPatterns = null,
            isOfficial = template.isOfficial,
            isParseable = template.isParseable,
            singleNumberInstruction = null,
            multipleNumberInstruction = null,
            createdAt = null
        )
        _state.update { current ->
            val updated = current.methods + newMethod
            saveMethodsToCache(updated)
            current.copy(methods = updated)
        }
        validateAndSyncSimToggles()
    }

    fun onSimNumberChanged(simSlot: Int, num: String) {
        val cleanNum = num.take(11).filter { it.isDigit() }
        _state.update {
            if (simSlot == 1) it.copy(sim1Number = cleanNum, pendingSimConflict = null)
            else it.copy(sim2Number = cleanNum, pendingSimConflict = null)
        }

        if (cleanNum.length != 11) return

        if (cleanNum == lastConfirmedLookupNumber[simSlot]) return

        if (simSlot == 1) {
            sim1NumberDebounceJob?.cancel()
            sim1NumberDebounceJob = viewModelScope.launch {
                delay(1500)
                val resolved = resolvePhysicalNumber(simSlot, cleanNum)
                if (resolved != cleanNum) {
                    _state.update {
                        if (simSlot == 1) it.copy(sim1Number = resolved) else it.copy(sim2Number = resolved)
                    }
                    _state.update {
                        it.copy(successMessage = "স্লট $simSlot-এ থাকা সিমের নম্বর ($resolved) অনুযায়ী আপডেট করা হয়েছে")
                    }
                    viewModelScope.launch {
                        delay(3000)
                        _state.update { it.copy(successMessage = null) }
                    }
                }
                if (resolved != lastConfirmedLookupNumber[simSlot]) {
                    lookupSlotNumber(simSlot, resolved)
                }
            }
        } else {
            sim2NumberDebounceJob?.cancel()
            sim2NumberDebounceJob = viewModelScope.launch {
                delay(1500)
                val resolved = resolvePhysicalNumber(simSlot, cleanNum)
                if (resolved != cleanNum) {
                    _state.update {
                        if (simSlot == 1) it.copy(sim1Number = resolved) else it.copy(sim2Number = resolved)
                    }
                    _state.update {
                        it.copy(successMessage = "স্লট $simSlot-এ থাকা সিমের নম্বর ($resolved) অনুযায়ী আপডেট করা হয়েছে")
                    }
                    viewModelScope.launch {
                        delay(3000)
                        _state.update { it.copy(successMessage = null) }
                    }
                }
                if (resolved != lastConfirmedLookupNumber[simSlot]) {
                    lookupSlotNumber(simSlot, resolved)
                }
            }
        }
    }

    fun dismissSimConflict() {
        val conflict = _state.value.pendingSimConflict ?: return
        val revertNum = lastConfirmedLookupNumber[conflict.simSlot].orEmpty()
        _state.update {
            if (conflict.simSlot == 1) {
                it.copy(sim1Number = revertNum, pendingSimConflict = null)
            } else {
                it.copy(sim2Number = revertNum, pendingSimConflict = null)
            }
        }
        loadGatewayMethods()
    }

    fun confirmForceShift() {
        val conflict = _state.value.pendingSimConflict ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, pendingSimConflict = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching {
                api.forceShiftSlot(
                    "Bearer $token",
                    SlotForceShiftRequest(
                        simSlot = conflict.simSlot,
                        phoneNumber = conflict.phoneNumber
                    )
                )
            }.onSuccess { res ->
                _state.update { it.copy(isSaving = false) }
                if (res.isSuccessful && res.body()?.success == true) {
                    val body = res.body()!!
                    body.data?.let { newData ->
                        applyServerMethods(newData)
                        lastConfirmedLookupNumber[conflict.simSlot] = conflict.phoneNumber
                    }
                    enableSimSlot(conflict.simSlot)
                    online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(
                        getApplication(),
                        0L,
                    )
                    loadTemplates(force = true)
                    loadGatewayMethods()
                    _state.update {
                        it.copy(successMessage = body.message ?: "সিম সফলভাবে স্থানান্তরিত হয়েছে।")
                    }
                    validateAndSyncSimToggles()
                    viewModelScope.launch {
                        delay(2500)
                        _state.update { it.copy(successMessage = null) }
                    }
                } else {
                    setError("সিম স্থানান্তর ব্যর্থ হয়েছে (${res.code()})")
                }
            }.onFailure { exception ->
                _state.update { it.copy(isSaving = false) }
                setError("নেটওয়ার্ক সমস্যা: ${exception.message}")
            }
        }
    }

    private suspend fun performSlotLookup(
        simSlot: Int,
        cleanNum: String,
        wipeLocalSlot: Boolean = true,
        skipValidate: Boolean = false
    ): Boolean {
        if (isApplyingProfile) return false
        val token = getToken() ?: return false
        return runCatching {
            api.lookupSlotNumber(
                "Bearer $token",
                SlotLookupRequest(simSlot = simSlot, phoneNumber = cleanNum)
            )
        }.fold(
            onSuccess = { res ->
                if (!res.isSuccessful) {
                    setError("নম্বর যাচাই ব্যর্থ হয়েছে (${res.code()})")
                    return@fold false
                }
                val body = res.body() ?: return@fold false
                if (body.hasConflict == true) {
                    _state.update {
                        it.copy(
                            pendingSimConflict = SimConflictUi(
                                simSlot = simSlot,
                                phoneNumber = cleanNum,
                                runningDeviceName = body.runningDeviceName ?: "অন্য ডিভাইস"
                            )
                        )
                    }
                    return@fold false
                }

                // টগল ON পাথে লোকাল সিলেকশন রাখতে wipe বন্ধ; নাম্বার চেঞ্জে wipe চালু।
                val preservedLocal = if (!wipeLocalSlot) {
                    _state.value.methods.filter { it.simSlot == simSlot }
                } else {
                    emptyList()
                }

                if (wipeLocalSlot) {
                    clearSlotLocalCache(simSlot)
                }

                if (!body.data.isNullOrEmpty()) {
                    applyServerMethods(body.data)
                } else {
                    mergeSlotNumbersFromServer(body.data)
                }

                if (preservedLocal.isNotEmpty()) {
                    mergePreservedLocalSlotMethods(simSlot, preservedLocal)
                }

                if (body.applyProfile == true && !body.cachedMethods.isNullOrEmpty()) {
                    applyProfileFromLookup(simSlot, cleanNum, body.cachedMethods)
                    // প্রোফাইল apply-এর পরও ইউজারের enabled সিলেকশন ধরে রাখি
                    if (preservedLocal.isNotEmpty()) {
                        mergePreservedLocalSlotMethods(
                            simSlot,
                            preservedLocal.filter { it.isEnabled == 1 }
                        )
                    }
                } else {
                    lastConfirmedLookupNumber[simSlot] = cleanNum
                }

                if (!skipValidate) {
                    validateAndSyncSimToggles()
                }
                true
            },
            onFailure = { exception ->
                setError("নেটওয়ার্ক সমস্যা: ${exception.message}")
                false
            }
        )
    }

    /**
     * Lookup/server data-এর সাথে লোকাল স্লট মেথড মার্জ — টেমপ্লেট সিলেক্ট করে SIM চালু
     * করলে যেন wipe/profile সেগুলো হারায় না।
     */
    private fun mergePreservedLocalSlotMethods(simSlot: Int, preserved: List<GatewayMethod>) {
        if (preserved.isEmpty()) return
        val current = _state.value.methods
        val existingTids = current
            .filter { it.simSlot == simSlot }
            .mapNotNull { it.templateId }
            .toSet()
        val toAdd = preserved.filter { method ->
            method.simSlot == simSlot &&
                method.templateId != null &&
                method.templateId !in existingTids
        }
        val enableIds = preserved
            .filter { it.isEnabled == 1 }
            .mapNotNull { it.templateId }
            .toSet()

        if (toAdd.isEmpty() && enableIds.isEmpty()) return

        var merged = if (toAdd.isNotEmpty()) current + toAdd else current
        if (enableIds.isNotEmpty()) {
            merged = merged.map { m ->
                if (m.simSlot == simSlot && m.templateId != null && m.templateId in enableIds) {
                    m.copy(isEnabled = 1)
                } else {
                    m
                }
            }
        }
        saveMethodsToCache(merged)
        _state.update { it.copy(methods = merged) }
    }

    private suspend fun lookupSlotNumber(simSlot: Int, cleanNum: String) {
        performSlotLookup(simSlot, cleanNum)
    }

    private fun mergeSlotNumbersFromServer(serverData: List<GatewayMethod>?) {
        if (serverData.isNullOrEmpty()) return
        val sim1Num = serverData.find { it.simSlot == 1 && !it.number.isNullOrEmpty() }?.number
            ?: _state.value.sim1Number
        val sim2Num = serverData.find { it.simSlot == 2 && !it.number.isNullOrEmpty() }?.number
            ?: _state.value.sim2Number
        _state.update { it.copy(sim1Number = sim1Num, sim2Number = sim2Num) }
    }

    private suspend fun applyProfileFromLookup(
        simSlot: Int,
        phoneNumber: String,
        cachedMethods: List<GatewayMethod>
    ) {
        val token = getToken() ?: return
        isApplyingProfile = true
        val items = cachedMethods.map { method ->
            BulkSyncMethodItem(
                templateId = method.templateId,
                provider = method.provider,
                isEnabled = method.isEnabled
            )
        }
        runCatching {
            api.bulkSyncSlotMethods(
                "Bearer $token",
                BulkSyncRequest(
                    simSlot = simSlot,
                    phoneNumber = phoneNumber,
                    methods = items,
                    replaceSlot = true,
                    activateBinding = false
                )
            )
        }.onSuccess { res ->
            if (res.isSuccessful && res.body()?.success == true) {
                res.body()?.data?.let { newData ->
                    applyServerMethods(newData)
                }
                lastConfirmedLookupNumber[simSlot] = phoneNumber
                loadTemplates(force = true)
            }
        }
        isApplyingProfile = false
    }

    fun validateAndSyncSimToggles() {
        if (_state.value.pendingSimConflict != null) return
        if (isSimToggleInFlight) return

        val stateVal = _state.value
        val sim1Num = stateVal.sim1Number
        val sim2Num = stateVal.sim2Number
        val methods = stateVal.methods

        // ---------------------------------------------------------------------
        // CHURN GUARD: টেমপ্লেট/মেথড এখনো সার্ভার থেকে পুরোপুরি লোড হয়নি এমন
        // অবস্থায় SIM জোর করে OFF করলে বারবার ON→OFF churn হয় (ইউজারের অভিযোগ)।
        // তাই — মেথড লিস্ট খালি থাকলে বা টেমপ্লেট এখনো লোড হচ্ছে/খালি থাকলে
        // toggle-এ হাত দিই না; শুধু re-activate পাস চালিয়ে যাই।
        // ---------------------------------------------------------------------
        val dataNotReady = methods.isEmpty() ||
            stateVal.isTemplatesLoading ||
            stateVal.templates.isEmpty()

        val hasActiveMethodSim1 = methods.any { it.simSlot == 1 && it.isEnabled == 1 }
        val hasActiveMethodSim2 = methods.any { it.simSlot == 2 && it.isEnabled == 1 }

        val isSim1Valid = sim1Num.length == 11 && hasActiveMethodSim1
        val isSim2Valid = sim2Num.length == 11 && hasActiveMethodSim2

        if (!dataNotReady) {
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

        // লগইন/রিস্টোরে যে slot গুলো ON + বৈধ, সেগুলো ব্যাকএন্ডে re-activate করে
        // sim_slot_bindings.is_active=1 নিশ্চিত করা হয় (নাহলে চেকআউট পেজে দেখা যায় না)।
        reactivateOnlineSlots(
            sim1On = _state.value.sim1Enabled && isSim1Valid,
            sim2On = _state.value.sim2Enabled && isSim2Valid
        )
    }

    /**
     * প্রতি সেশনে একবার — লোকালি ON এবং বৈধ slot গুলো সার্ভারে re-activate করে।
     * ম্যানুয়াল toggle ছাড়া (যেমন লগইন-রিস্টোর) is_active সার্ভারে 0 থেকে যেত;
     * এখানে conflict-safe ভাবে bulk-sync(activateBinding=true) কল করে তা 1 করা হয়।
     */
    private fun reactivateOnlineSlots(sim1On: Boolean, sim2On: Boolean) {
        if (isReactivatingSlots) return
        if (_state.value.pendingSimConflict != null) return

        val targets = buildList {
            if (sim1On && 1 !in reactivatedSlotsThisSession) add(1)
            if (sim2On && 2 !in reactivatedSlotsThisSession) add(2)
        }
        if (targets.isEmpty()) return

        isReactivatingSlots = true
        viewModelScope.launch {
            try {
                val token = getToken() ?: return@launch
                for (simSlot in targets) {
                    val stateVal = _state.value
                    val phoneNumber = if (simSlot == 1) stateVal.sim1Number else stateVal.sim2Number
                    if (phoneNumber.length != 11) continue

                    val bulkItems = stateVal.methods
                        .filter { it.simSlot == simSlot && it.isEnabled == 1 }
                        .map { method ->
                            BulkSyncMethodItem(
                                templateId = method.templateId,
                                provider = method.provider,
                                isEnabled = method.isEnabled
                            )
                        }
                    if (bulkItems.isEmpty()) continue

                    runCatching {
                        api.bulkSyncSlotMethods(
                            "Bearer $token",
                            BulkSyncRequest(
                                simSlot = simSlot,
                                phoneNumber = phoneNumber,
                                methods = bulkItems,
                                replaceSlot = false,
                                activateBinding = true
                            )
                        )
                    }.onSuccess { res ->
                        val body = res.body()
                        if (res.isSuccessful && body?.success == true && body.hasConflict != true) {
                            reactivatedSlotsThisSession.add(simSlot)
                            body.data?.let { newData ->
                                saveMethodsToCache(newData)
                                _state.update { it.copy(methods = newData) }
                            }
                        }
                    }
                }
            } finally {
                isReactivatingSlots = false
            }
        }
    }

    fun loadTemplates(force: Boolean = false) {
        if (!force && _state.value.templates.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTemplatesLoading = true) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            val lastSync = online.paychek.app.data.local.prefs.PrefsHelper.getGatewayMethodsLastSync(getApplication())
            runCatching { api.getTemplates("Bearer $token", lastSync) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        val body = res.body()!!
                        body.dataVersion?.takeIf { it > 0 }?.let {
                            online.paychek.app.data.local.prefs.PrefsHelper.setGatewayMethodsLastSync(getApplication(), it)
                        }

                        val list = body.templates
                        if (list != null) {
                            _state.update { it.copy(templates = list, isTemplatesLoading = false) }
                            try {
                                val json = online.paychek.app.utils.GsonUtils.gson.toJson(list)
                                val ok = online.paychek.app.data.local.prefs.PrefsHelper
                                    .setSmsTemplatesCache(getApplication(), json)
                                if (!ok) {
                                    android.util.Log.e("DeviceViewModel", "SMS templates cache save/verify failed")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            // unchanged / null payload — force full fetch if local cache empty
                            val localEmpty = !online.paychek.app.data.local.prefs.PrefsHelper
                                .hasSmsTemplatesCache(getApplication()) && _state.value.templates.isEmpty()
                            if (localEmpty) {
                                runCatching { api.getTemplates("Bearer $token", 0L) }
                                    .onSuccess { fullRes ->
                                        val fullList = fullRes.body()?.templates
                                        if (fullRes.isSuccessful && fullRes.body()?.success == true && fullList != null) {
                                            fullRes.body()?.dataVersion?.takeIf { it > 0 }?.let {
                                                online.paychek.app.data.local.prefs.PrefsHelper
                                                    .setGatewayMethodsLastSync(getApplication(), it)
                                            }
                                            _state.update { it.copy(templates = fullList, isTemplatesLoading = false) }
                                            val json = online.paychek.app.utils.GsonUtils.gson.toJson(fullList)
                                            online.paychek.app.data.local.prefs.PrefsHelper
                                                .setSmsTemplatesCache(getApplication(), json)
                                        } else {
                                            _state.update { it.copy(isTemplatesLoading = false) }
                                        }
                                    }
                                    .onFailure { _state.update { it.copy(isTemplatesLoading = false) } }
                            } else {
                                _state.update { it.copy(isTemplatesLoading = false) }
                            }
                        }
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
        if (!_state.value.hasTemplatePermission) {
            _state.update {
                it.copy(
                    showPermissionDialog = true,
                    permissionDialogMessage = "আপনার প্যাকেজে টেমপ্লেট যোগ করার পারমিশন নেই।"
                )
            }
            return
        }
        val existingMethod = _state.value.methods.find { it.simSlot == simSlot && it.templateId == template.id }
        if (existingMethod != null) {
            toggleMethod(existingMethod)
            return
        }

        if (!isSimSlotActive(simSlot)) {
            addLocalTemplateMethod(simSlot, template)
            return
        }

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





    private fun setError(msg: String) {
        _state.update { it.copy(isLoading = false, isSaving = false, errorMessage = msg) }
    }

    fun clearDialogErrorMessage() {
        _state.update { it.copy(dialogErrorMessage = null) }
    }

    private fun performDropSync() {
        val currentTemplates = _state.value.templates
        val currentMethods = _state.value.methods

        // ---------------------------------------------------------------------
        // DROP GUARD: টেমপ্লেট লিস্ট এখনো লোড হচ্ছে বা খালি থাকা অবস্থায় কোনো
        // method কে "unknown template" ধরে drop করা যাবে না — আংশিক লোডে বৈধ
        // method drop হয়ে SIM OFF হয়ে যায় (churn-এর অন্যতম কারণ)। শুধু টেমপ্লেট
        // সম্পূর্ণ লোড ও non-empty হলেই drop যুক্তিসঙ্গত।
        // ---------------------------------------------------------------------
        if (_state.value.isTemplatesLoading) return
        if (currentTemplates.isEmpty()) return
        
        val activeTemplateIds = currentTemplates.mapNotNull { it.id }.toSet()
        
        val methodsToDrop = currentMethods.filter { method ->
            (method.isOfficial ?: 1) != 0 &&
                method.templateId != null &&
                !activeTemplateIds.contains(method.templateId)
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

    fun setShowPremiumUpgradeDialog(show: Boolean) {
        _state.update { it.copy(showPremiumUpgradeDialog = show) }
    }

    fun onAddCustomSenderClick(simSlot: Int, onAllowed: (Int) -> Unit) {
        if (!_state.value.hasCustomSenderPermission) {
            _state.update { it.copy(showPremiumUpgradeDialog = true) }
            return
        }
        _state.update { it.copy(dialogErrorMessage = null) }
        onAllowed(simSlot)
    }

    private fun loadAccountEntitlements() {
        viewModelScope.launch {
            val ent = online.paychek.app.utils.AccountEntitlementsStore.refresh(getApplication())
                ?: online.paychek.app.utils.AccountEntitlementsStore.readCached(getApplication())
            _state.update {
                it.copy(
                    hasCustomSenderPermission = ent.hasCustomSender,
                    hasTemplatePermission = ent.hasTemplate,
                    hasDevicePermission = ent.hasDevice
                )
            }
        }
    }

    fun refreshAccountEntitlements() = loadAccountEntitlements()

    fun addCustomSender(
        simSlot: Int,
        senderId: String,
        officialTemplateId: Int? = null,
        createPersonal: Boolean = false,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, dialogErrorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            val deviceId = DeviceIdHelper.getHashedAndroidId(getApplication())
            val request = AddCustomSenderRequest(
                simSlot = simSlot,
                senderId = senderId.trim(),
                deviceId = deviceId,
                officialTemplateId = officialTemplateId,
                createPersonal = if (createPersonal) true else null
            )

            runCatching { api.addCustomSender("Bearer $token", request) }
                .onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            saveMethodsToCache(newData)
                            _state.update { it.copy(methods = newData) }
                        }
                        loadTemplates(force = true)
                        onSuccess()
                    } else if (res.code() == 403) {
                        _state.update { it.copy(showPremiumUpgradeDialog = true) }
                    } else {
                        val msg = online.paychek.app.utils.ApiErrorParser.parse(res.errorBody()?.string())
                            ?: "মেথড যোগ করতে ব্যর্থ হয়েছে (${res.code()})"
                        _state.update { it.copy(dialogErrorMessage = msg) }
                    }
                }
                .onFailure { exception ->
                    _state.update { it.copy(isSaving = false, dialogErrorMessage = "নেটওয়ার্ক সমস্যা: ${exception.message}") }
                }
        }
    }

    fun deleteCustomSender(methodId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")

            runCatching { api.deleteGatewayMethod("Bearer $token", methodId) }
                .onSuccess { res ->
                    _state.update { it.copy(isSaving = false) }
                    if (res.isSuccessful && res.body()?.success == true) {
                        res.body()?.data?.let { newData ->
                            saveMethodsToCache(newData)
                            _state.update { it.copy(methods = newData) }
                        }
                        loadGatewayMethods()
                    } else {
                        setError("মেথড ডিলিট করতে ব্যর্থ হয়েছে (${res.code()})")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isSaving = false) }
                    setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                }
        }
    }

    fun syncAndValidateSimSwap(simSlot: Int, phoneNumber: String) {
        viewModelScope.launch {
            lookupSlotNumber(simSlot, phoneNumber.trim().filter { it.isDigit() }.take(11))
        }
    }

    private fun isCustomSenderPermissionActive(hasAddon: Int, endsAt: String?, role: String): Boolean {
        if (role == "admin") return true
        if (hasAddon != 1) return false
        if (endsAt.isNullOrBlank()) return true
        return try {
            val parts = endsAt.take(10).split("-")
            if (parts.size != 3) return true
            val endCal = java.util.Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 23, 59, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            endCal.timeInMillis >= System.currentTimeMillis()
        } catch (_: Exception) {
            true
        }
    }

    fun setShowAccountNumbersSheet(show: Boolean) {
        _state.update { it.copy(showAccountNumbersSheet = show) }
        if (show) loadAccountNumbers()
    }

    fun loadAccountNumbers() {
        viewModelScope.launch {
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            _state.update { it.copy(isAccountNumbersLoading = true) }
            val deviceId = DeviceIdHelper.getHashedAndroidId(getApplication())

            NumberHeartbeatEngine.pulse(getApplication())
            delay(400)

            runCatching { api.getAccountNumbers("Bearer $token", deviceId) }
                .onSuccess { res ->
                    if (res.isSuccessful && res.body()?.success == true) {
                        _state.update {
                            it.copy(
                                accountNumbers = res.body()?.data.orEmpty(),
                                isAccountNumbersLoading = false
                            )
                        }
                    } else {
                        _state.update { it.copy(isAccountNumbersLoading = false) }
                        setError("নাম্বার লিস্ট লোড ব্যর্থ (${res.code()})")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isAccountNumbersLoading = false) }
                    setError("নেটওয়ার্ক সমস্যা: ${it.message}")
                }
        }
    }

    fun requestDeleteAccountNumber(item: AccountNumberDto) {
        _state.update { it.copy(accountNumberToDelete = item) }
    }

    fun dismissDeleteAccountNumber() {
        _state.update { it.copy(accountNumberToDelete = null) }
    }

    fun confirmDeleteAccountNumber() {
        val item = _state.value.accountNumberToDelete ?: return
        viewModelScope.launch {
            val token = getToken() ?: return@launch setError("লগইন সেশন পাওয়া যায়নি।")
            _state.update { it.copy(isDeletingAccountNumber = true) }
            val deviceId = DeviceIdHelper.getHashedAndroidId(getApplication())
            runCatching {
                api.deleteAccountNumber(
                    "Bearer $token",
                    DeleteAccountNumberRequest(item.phoneNumber),
                    deviceId
                )
            }.onSuccess { res ->
                _state.update { it.copy(isDeletingAccountNumber = false, accountNumberToDelete = null) }
                if (res.isSuccessful && res.body()?.success == true) {
                    val msg = res.body()?.message ?: "নাম্বার মুছে ফেলা হয়েছে"
                    _state.update {
                        it.copy(
                            successMessage = msg,
                            accountNumbers = it.accountNumbers.filter { n -> n.phoneNumber != item.phoneNumber }
                        )
                    }
                    loadGatewayMethods()
                    loadTemplates()
                } else {
                    setError(res.body()?.error ?: "নাম্বার মুছতে ব্যর্থ (${res.code()})")
                }
            }.onFailure {
                _state.update { it.copy(isDeletingAccountNumber = false, accountNumberToDelete = null) }
                setError("নেটওয়ার্ক সমস্যা: ${it.message}")
            }
        }
    }
}
