package online.paychek.app.services.smartpopup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.paychek.app.MainActivity
import online.paychek.app.R
import online.paychek.app.config.AppConfig
import online.paychek.app.data.remote.dto.TransactionItem
import online.paychek.app.data.repository.PaymentRepository
import online.paychek.app.utils.SecurePreferences

class SmartPopupService : Service() {

    companion object {
        const val ACTION_OPEN = "online.paychek.app.SMART_POPUP_OPEN"
        const val ACTION_CLOSE = "online.paychek.app.SMART_POPUP_CLOSE"
        const val ACTION_CACHE_UPDATED = "online.paychek.app.SMART_POPUP_CACHE_UPDATED"
        const val ACTION_SCAN_BOUNDS = "online.paychek.app.SMART_POPUP_SCAN_BOUNDS"
        const val ACTION_SCAN_RESULT = "online.paychek.app.SMART_POPUP_SCAN_RESULT"
        const val EXTRA_QUERY = "query"

        private const val CHANNEL_ID = "smart_popup"
        private const val NOTIF_ID = 9101
        private const val PREF = "smart_popup_window_v1"
        private const val AUTO_CLOSE_MS = 60 * 60 * 1000L
    }

    private lateinit var wm: WindowManager
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cropView: View? = null

    private var midContainer: View? = null
    private var resultScroll: View? = null
    private var rvResults: RecyclerView? = null
    private var rvSession: RecyclerView? = null
    private var etQuery: EditText? = null
    private var tvStatus: TextView? = null
    private var tvSms: TextView? = null
    private var btnHistory: ImageButton? = null
    private var btnClear: ImageButton? = null
    private var hasResultCards: Boolean = false
    private var searchGeneration: Int = 0

    private lateinit var resultAdapter: SmartPopupTransactionAdapter
    private lateinit var sessionAdapter: SmartPopupTransactionAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = PaymentRepository()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoCloseRunnable = Runnable {
        Toast.makeText(this, "স্মার্ট পপ-আপ ১ ঘণ্টা পর বন্ধ হয়েছে", Toast.LENGTH_LONG).show()
        closeAll()
        stopSelf()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CACHE_UPDATED -> refreshIdleStatus()
                ACTION_SCAN_RESULT -> {
                    val query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
                    if (query.isNotBlank()) {
                        SmartPopupState.currentQuery = query
                        SmartPopupState.isScanning = false
                        showPanel()
                        applySearch(query)
                    } else {
                        Toast.makeText(this@SmartPopupService, "স্কয়ারের ভিতরে TrxID পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        showPanel()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        resultAdapter = SmartPopupTransactionAdapter { markSoldOut(it) }
        sessionAdapter = SmartPopupTransactionAdapter { markSoldOut(it) }

        val filter = IntentFilter().apply {
            addAction(ACTION_CACHE_UPDATED)
            addAction(ACTION_SCAN_RESULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE -> {
                cancelAutoClose()
                closeAll()
                stopSelf()
            }
            ACTION_OPEN, null -> {
                if (panelView == null && bubbleView == null) {
                    SmartPopupState.clearSession()
                }
                showPanel()
                scheduleAutoClose()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelAutoClose()
        runCatching { unregisterReceiver(receiver) }
        closeAll()
        SmartPopupState.isOpen = false
        SmartPopupState.isMinimized = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showPanel() {
        removeBubble()
        removeCropBox()
        if (panelView != null) {
            SmartPopupState.isMinimized = false
            renderMode()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.smart_popup_panel, null)
        panelView = view
        midContainer = view.findViewById(R.id.smartPopupMid)
        resultScroll = view.findViewById(R.id.smartPopupResultScroll)
        rvResults = view.findViewById(R.id.rvSmartPopupResults)
        rvSession = view.findViewById(R.id.rvSmartPopupSession)
        etQuery = view.findViewById(R.id.tvSmartPopupQuery)
        tvStatus = view.findViewById(R.id.tvSmartPopupStatus)
        tvSms = view.findViewById(R.id.tvSmartPopupSms)
        btnHistory = view.findViewById(R.id.btnSmartPopupHistory)
        btnClear = view.findViewById(R.id.btnSmartPopupClear)

        rvResults?.layoutManager = LinearLayoutManager(this)
        rvResults?.adapter = resultAdapter
        rvSession?.layoutManager = LinearLayoutManager(this)
        rvSession?.adapter = sessionAdapter

        wireSearchBox()

        val screen = screenSize()
        val margin = dp(12)
        val defaultW = screen.first - margin * 2
        val defaultH = compactHeight()

        val sp = getSharedPreferences(PREF, MODE_PRIVATE)
        val params = buildParams(defaultW, defaultH).apply {
            x = sp.getInt("x", margin)
            y = sp.getInt("y", dp(56))
            width = defaultW
            height = defaultH
        }
        panelParams = params

        view.findViewById<View>(R.id.btnSmartPopupClose).setOnClickListener {
            cancelAutoClose()
            closeAll()
            stopSelf()
        }
        view.findViewById<View>(R.id.btnSmartPopupMinimize).setOnClickListener { dockBubble() }
        view.findViewById<View>(R.id.btnSmartPopupScan).setOnClickListener { showCropBox() }
        btnHistory?.setOnClickListener { toggleSessionHistory() }
        btnClear?.setOnClickListener { clearSearch() }

        makeDraggable(view.findViewById(R.id.smartPopupHeader), view, params)
        attachResizeHandles(
            view.findViewById(R.id.smartPopupResizeLeft),
            view.findViewById(R.id.smartPopupResizeRight),
            view,
            params
        )

        wm.addView(view, params)
        SmartPopupState.isOpen = true
        SmartPopupState.isMinimized = false
        refreshIdleStatus()
        renderMode()
        if (SmartPopupState.currentQuery.isNotBlank()) {
            applySearch(SmartPopupState.currentQuery)
        }
    }

    private fun wireSearchBox() {
        val et = etQuery ?: return
        et.isFocusable = true
        et.isFocusableInTouchMode = true
        et.isCursorVisible = true
        et.setText(SmartPopupState.currentQuery)
        updateClearVisibility(et.text?.toString().orEmpty())
        et.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                enablePanelIme()
                v.performClick()
            }
            false
        }
        et.setOnClickListener { enablePanelIme() }
        et.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) enablePanelIme() else disablePanelIme()
        }
        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val q = et.text?.toString().orEmpty().trim()
                SmartPopupState.currentQuery = q
                applySearch(q)
                hideKeyboard()
                disablePanelIme()
                true
            } else {
                false
            }
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateClearVisibility(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun updateClearVisibility(text: String) {
        btnClear?.visibility = if (text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun clearSearch() {
        etQuery?.setText("")
        SmartPopupState.currentQuery = ""
        hasResultCards = false
        resultAdapter.submitList(emptyList())
        tvSms?.visibility = View.GONE
        SmartPopupState.viewMode = SmartPopupState.ViewMode.IDLE
        hideKeyboard()
        disablePanelIme()
        updateClearVisibility("")
        renderMode()
    }

    private fun enablePanelIme() {
        val view = panelView ?: return
        val params = panelParams ?: return
        // Overlay must be focusable for soft keyboard / EditText
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        runCatching { wm.updateViewLayout(view, params) }
        etQuery?.requestFocus()
        etQuery?.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etQuery, InputMethodManager.SHOW_FORCED)
        }
    }

    private fun disablePanelIme() {
        val view = panelView ?: return
        val params = panelParams ?: return
        params.flags = (params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun hideKeyboard() {
        val et = etQuery ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(et.windowToken, 0)
        et.clearFocus()
    }

    private fun compactHeight(): Int = WindowManager.LayoutParams.WRAP_CONTENT

    /** Idle-sized panel + room for the "no result" status line. */
    private fun noResultHeight(): Int = WindowManager.LayoutParams.WRAP_CONTENT

    private fun expandedHeight(): Int {
        val screen = screenSize()
        return (screen.second * 0.38f).toInt().coerceIn(dp(260), dp(420))
    }

    private fun resizePanel(heightPx: Int) {
        val view = panelView ?: return
        val params = panelParams ?: return
        params.height = heightPx
        val content = view.findViewById<View>(R.id.smartPopupContent)
        val expanded = heightPx != WindowManager.LayoutParams.WRAP_CONTENT
        content?.layoutParams = content.layoutParams?.apply {
            height = if (expanded) {
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        view.layoutParams?.let {
            // FrameLayout root follows window height mode
        }
        // When expanded, mid fills leftover space via weight; when compact mid is GONE
        midContainer?.layoutParams = (midContainer?.layoutParams as? android.widget.LinearLayout.LayoutParams)?.apply {
            height = if (expanded) 0 else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            weight = if (expanded) 1f else 0f
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun renderMode() {
        val mode = SmartPopupState.viewMode
        val mid = midContainer
        when (mode) {
            SmartPopupState.ViewMode.IDLE -> {
                mid?.visibility = View.GONE
                resultScroll?.visibility = View.GONE
                rvSession?.visibility = View.GONE
                resizePanel(compactHeight())
                refreshIdleStatus()
            }
            SmartPopupState.ViewMode.RESULT -> {
                rvSession?.visibility = View.GONE
                if (hasResultCards) {
                    mid?.visibility = View.VISIBLE
                    resultScroll?.visibility = View.VISIBLE
                    resizePanel(expandedHeight())
                } else {
                    // Compact like idle — only status line (no huge empty mid)
                    mid?.visibility = View.GONE
                    resultScroll?.visibility = View.GONE
                    tvSms?.visibility = View.GONE
                    resizePanel(noResultHeight())
                }
            }
            SmartPopupState.ViewMode.SESSION_HISTORY -> {
                mid?.visibility = View.VISIBLE
                resultScroll?.visibility = View.GONE
                rvSession?.visibility = View.VISIBLE
                sessionAdapter.submitList(SmartPopupState.sessionItems())
                tvStatus?.text = "সেশন হিস্টোরি · ${SmartPopupState.sessionItems().size} টি"
                resizePanel(expandedHeight())
            }
        }
        btnHistory?.visibility =
            if (SmartPopupState.hasSessionHistory()) View.VISIBLE else View.GONE
    }

    private fun toggleSessionHistory() {
        SmartPopupState.viewMode =
            if (SmartPopupState.viewMode == SmartPopupState.ViewMode.SESSION_HISTORY) {
                if (SmartPopupState.currentQuery.isNotBlank()) {
                    SmartPopupState.ViewMode.RESULT
                } else {
                    SmartPopupState.ViewMode.IDLE
                }
            } else {
                SmartPopupState.ViewMode.SESSION_HISTORY
            }
        renderMode()
        if (SmartPopupState.viewMode == SmartPopupState.ViewMode.RESULT) {
            applySearch(SmartPopupState.currentQuery)
        }
    }

    private fun dockBubble() {
        removeBubble()
        panelView?.let { v ->
            panelParams?.let { p -> saveWindow(p) }
            runCatching { wm.removeView(v) }
        }
        panelView = null
        panelParams = null
        midContainer = null
        resultScroll = null
        rvResults = null
        rvSession = null
        etQuery = null
        tvStatus = null
        tvSms = null
        btnHistory = null
        btnClear = null
        SmartPopupState.isMinimized = true

        val view = LayoutInflater.from(this).inflate(R.layout.smart_popup_bubble, null)
        bubbleView = view
        val params = buildParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val sp = getSharedPreferences(PREF, MODE_PRIVATE)
            x = sp.getInt("bx", screenSize().first - dp(52))
            y = sp.getInt("by", dp(200))
        }
        bubbleParams = params

        var initX = 0; var initY = 0; var initRx = 0f; var initRy = 0f; var moved = false
        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initRx = ev.rawX; initRy = ev.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (ev.rawX - initRx).toInt()
                    params.y = initY + (ev.rawY - initRy).toInt()
                    wm.updateViewLayout(view, params)
                    if (kotlin.math.abs(ev.rawX - initRx) > 8 || kotlin.math.abs(ev.rawY - initRy) > 8) {
                        moved = true
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved && ev.actionMasked == MotionEvent.ACTION_UP) showPanel()
                    getSharedPreferences(PREF, MODE_PRIVATE).edit()
                        .putInt("bx", params.x).putInt("by", params.y).apply()
                    true
                }
                else -> false
            }
        }

        wm.addView(view, params)
        view.post {
            params.x = screenSize().first - view.width - dp(4)
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /** Hide panel for scan without showing bubble (avoids double overlay). */
    private fun hidePanelForScan() {
        panelView?.let { v ->
            panelParams?.let { p -> saveWindow(p) }
            runCatching { wm.removeView(v) }
        }
        panelView = null
        panelParams = null
        midContainer = null
        resultScroll = null
        rvResults = null
        rvSession = null
        etQuery = null
        tvStatus = null
        tvSms = null
        btnHistory = null
        btnClear = null
        removeBubble()
        SmartPopupState.isMinimized = true
    }

    private fun showCropBox() {
        if (cropView != null) return
        if (!online.paychek.app.services.accessibility.PaychekAccessibilityService.isRunning) {
            Toast.makeText(
                this,
                "স্ক্যান করতে Accessibility (Paychek Background Guard) চালু করুন",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        hidePanelForScan()
        SmartPopupState.isScanning = true

        val view = LayoutInflater.from(this).inflate(R.layout.smart_popup_crop_box, null)
        cropView = view
        val boxW = dp(140)
        val boxH = dp(28)
        val screen = screenSize()
        val params = buildParams(boxW, boxH).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screen.first - boxW) / 2
            y = (screen.second - boxH) / 2
            // Keep crop above other overlays but never focusable so SMS stays the a11y root
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        wm.addView(view, params)

        var initX = 0; var initY = 0; var initRx = 0f; var initRy = 0f
        view.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initRx = ev.rawX; initRy = ev.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (ev.rawX - initRx).toInt()
                    params.y = initY + (ev.rawY - initRy).toInt()
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Exact on-screen bounds of the square — no padding (padding caused
                    // outside text like "Grameenphone" to enter the scan region).
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)
                    val rect = Rect(
                        loc[0],
                        loc[1],
                        loc[0] + view.width,
                        loc[1] + view.height
                    )
                    removeCropBox()
                    // Let crop overlay disappear before screenshot/OCR
                    mainHandler.postDelayed({
                        sendBroadcast(
                            Intent(ACTION_SCAN_BOUNDS).apply {
                                setPackage(packageName)
                                putExtra("rect", rect)
                            }
                        )
                    }, 120L)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCropBox()
                    showPanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun scheduleAutoClose() {
        mainHandler.removeCallbacks(autoCloseRunnable)
        mainHandler.postDelayed(autoCloseRunnable, AUTO_CLOSE_MS)
    }

    private fun cancelAutoClose() {
        mainHandler.removeCallbacks(autoCloseRunnable)
    }

    private fun applySearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            searchGeneration++
            etQuery?.setText("")
            updateClearVisibility("")
            hasResultCards = false
            resultAdapter.submitList(emptyList())
            tvSms?.visibility = View.GONE
            SmartPopupState.viewMode = SmartPopupState.ViewMode.IDLE
            renderMode()
            return
        }
        if (etQuery?.text?.toString() != q) {
            etQuery?.setText(q)
            etQuery?.setSelection(q.length)
        }
        updateClearVisibility(q)
        SmartPopupState.currentQuery = q

        val local = SmartPopupCache.search(this, q)
        if (local.isNotEmpty()) {
            renderSearchResults(local)
            return
        }

        // Local miss (e.g. SMS arrived after popup opened) → fetch from server
        val gen = ++searchGeneration
        hasResultCards = false
        resultAdapter.submitList(emptyList())
        tvSms?.visibility = View.GONE
        SmartPopupState.viewMode = SmartPopupState.ViewMode.RESULT
        tvStatus?.text = "সার্ভারে খোঁজা হচ্ছে..."
        renderMode()

        scope.launch {
            val token = SecurePreferences.decrypt(this@SmartPopupService, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) {
                if (gen != searchGeneration) return@launch
                renderSearchResults(emptyList())
                return@launch
            }
            val remote = withContext(Dispatchers.IO) {
                repo.findTransactionsByTrxId(token, q)
            }
            if (gen != searchGeneration) return@launch
            remote.fold(
                onSuccess = { items ->
                    // Prefer exact TrxID match (server may return case-variants)
                    val matched = items.filter { it.trxId.equals(q, ignoreCase = true) }
                        .ifEmpty { items }
                    if (matched.isNotEmpty()) {
                        SmartPopupCache.upsert(this@SmartPopupService, matched)
                    }
                    renderSearchResults(matched)
                },
                onFailure = {
                    renderSearchResults(emptyList())
                }
            )
        }
    }

    private fun renderSearchResults(results: List<TransactionItem>) {
        SmartPopupState.trackSessionItems(results)
        resultAdapter.submitList(results)
        hasResultCards = results.isNotEmpty()

        val sms = results.firstOrNull()?.fullSms?.takeIf { it.isNotBlank() }
        if (sms != null) {
            tvSms?.visibility = View.VISIBLE
            tvSms?.text = sms
        } else {
            tvSms?.visibility = View.GONE
        }

        SmartPopupState.viewMode = SmartPopupState.ViewMode.RESULT
        tvStatus?.text = if (results.isEmpty()) {
            "কোনো ট্রানজেকশন পাওয়া যায়নি"
        } else {
            "${results.size} টি ফলাফল"
        }
        renderMode()
    }

    private fun refreshIdleStatus() {
        if (SmartPopupState.viewMode != SmartPopupState.ViewMode.IDLE) return
        val count = SmartPopupCache.load(this).size
        tvStatus?.text = if (count > 0) {
            getString(R.string.smart_popup_history_loaded)
        } else {
            "লোড হচ্ছে..."
        }
    }

    private fun markSoldOut(item: TransactionItem) {
        scope.launch {
            val token = SecurePreferences.decrypt(this@SmartPopupService, AppConfig.KEY_AUTH_TOKEN)
            if (token.isEmpty()) return@launch
            val result = withContext(Dispatchers.IO) {
                repo.markTransactionSoldOut(token, item.id)
            }
            result.onSuccess {
                SmartPopupCache.markSoldOutLocal(this@SmartPopupService, item.id)
                SmartPopupState.trackSoldOut(item)
                applySearch(SmartPopupState.currentQuery)
                btnHistory?.visibility = View.VISIBLE
                Toast.makeText(this@SmartPopupService, "Sold out", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@SmartPopupService, it.message ?: "ব্যর্থ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun makeDraggable(handle: View, root: View, params: WindowManager.LayoutParams) {
        var initX = 0; var initY = 0; var initRx = 0f; var initRy = 0f
        var dragging = false
        handle.setOnTouchListener { _, ev ->
            // Let search EditText / clear button receive taps (don't steal for drag)
            if (ev.action == MotionEvent.ACTION_DOWN && isTouchOnSearchControls(ev)) {
                dragging = false
                return@setOnTouchListener false
            }
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initRx = ev.rawX; initRy = ev.rawY
                    dragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@setOnTouchListener false
                    params.x = initX + (ev.rawX - initRx).toInt()
                    params.y = initY + (ev.rawY - initRy).toInt()
                    wm.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) saveWindow(params)
                    dragging = false
                    false
                }
                else -> false
            }
        }
    }

    private fun isTouchOnSearchControls(ev: MotionEvent): Boolean {
        val ids = intArrayOf(R.id.smartPopupSearchBar, R.id.tvSmartPopupQuery, R.id.btnSmartPopupClear)
        for (id in ids) {
            val v = panelView?.findViewById<View>(id) ?: continue
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()
            if (x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height) {
                return true
            }
        }
        return false
    }

    private fun attachResizeHandles(
        left: View,
        right: View,
        root: View,
        params: WindowManager.LayoutParams
    ) {
        val minW = dp(260)
        val minH = compactHeight()
        val maxW = (screenSize().first * 0.98f).toInt()
        val maxH = (screenSize().second * 0.55f).toInt()
        var startW = 0; var startX = 0; var startRx = 0f

        right.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = params.width; startRx = ev.rawX; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.width = (startW + (ev.rawX - startRx).toInt()).coerceIn(minW, maxW)
                    wm.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveWindow(params); false
                }
                else -> false
            }
        }

        left.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = params.width; startX = params.x; startRx = ev.rawX; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - startRx).toInt()
                    val newW = (startW - dx).coerceIn(minW, maxW)
                    params.width = newW
                    params.x = startX + (startW - newW)
                    wm.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveWindow(params); false
                }
                else -> false
            }
        }

        params.height = params.height.coerceIn(minH, maxH)
    }

    private fun saveWindow(p: WindowManager.LayoutParams) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
            .putInt("x", p.x).putInt("y", p.y)
            .putInt("w", p.width).putInt("h", p.height)
            .apply()
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { wm.removeView(it) } }
        bubbleView = null
        bubbleParams = null
    }

    private fun removeCropBox() {
        cropView?.let { runCatching { wm.removeView(it) } }
        cropView = null
    }

    private fun closeAll() {
        removeCropBox()
        removeBubble()
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        panelParams = null
        SmartPopupState.isOpen = false
        SmartPopupState.isMinimized = false
        SmartPopupState.isScanning = false
        SmartPopupState.clearSession()
    }

    private fun buildParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.width to wm.defaultDisplay.height
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Smart Pop-up", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.smart_popup_notif_title))
            .setContentText(getString(R.string.smart_popup_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
