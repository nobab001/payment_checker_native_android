package online.paychek.app.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import online.paychek.app.services.smartpopup.SmartPopupScanHelper
import online.paychek.app.services.smartpopup.SmartPopupService

/**
 * Accessibility — Smart Pop-up OCR / region scan only.
 * Not used for process keep-alive or FGS healing (SMS ingest is SmsReceiver).
 */
class PaychekAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != SmartPopupService.ACTION_SCAN_BOUNDS) return
            val rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("rect", Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("rect")
            } ?: return

            // Crop overlay already removed — settle before screenshot/OCR
            handler.postDelayed({ performRegionScan(rect) }, 220L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility connected — Smart Popup scan only")

        val filter = IntentFilter(SmartPopupService.ACTION_SCAN_BOUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanReceiver, filter)
        }

        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 1000
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Scan is broadcast-driven (Smart Popup).
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(scanReceiver) }
        super.onDestroy()
    }

    private fun performRegionScan(rect: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scanCropWithOcr(rect)
        } else {
            finishWithA11yFallback(rect)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun scanCropWithOcr(rect: Rect) {
        Log.i(TAG, "OCR crop scan rect=$rect")
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hwBuf = screenshot.hardwareBuffer
                    val hwBitmap = try {
                        Bitmap.wrapHardwareBuffer(hwBuf, screenshot.colorSpace)
                    } catch (e: Exception) {
                        Log.w(TAG, "wrapHardwareBuffer failed: ${e.message}")
                        hwBuf.close()
                        finishWithA11yFallback(rect)
                        return
                    }
                    hwBuf.close()
                    val soft = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap?.recycle()
                    if (soft == null) {
                        finishWithA11yFallback(rect)
                        return
                    }
                    val cropped = cropBitmapSafe(soft, rect)
                    if (cropped !== soft) soft.recycle()
                    ocrCropBitmap(cropped) { ocrHit ->
                        if (!ocrHit.isNullOrBlank()) {
                            Log.i(TAG, "OCR hit='$ocrHit'")
                            vibrateSuccess()
                            broadcastScanResult(ocrHit)
                        } else {
                            Log.i(TAG, "OCR empty — a11y fallback")
                            finishWithA11yFallback(rect)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed code=$errorCode — a11y fallback")
                    finishWithA11yFallback(rect)
                }
            }
        )
    }

    private fun ocrCropBitmap(bitmap: Bitmap, onDone: (String?) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.orEmpty()
                Log.i(TAG, "OCR raw='${raw.take(120)}'")
                val hit = SmartPopupScanHelper.extractFromText(raw)
                recognizer.close()
                bitmap.recycle()
                onDone(hit)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR failed: ${e.message}")
                recognizer.close()
                bitmap.recycle()
                onDone(null)
            }
    }

    private fun cropBitmapSafe(original: Bitmap, rect: Rect): Bitmap {
        val x = rect.left.coerceIn(0, original.width - 1)
        val y = rect.top.coerceIn(0, original.height - 1)
        val right = rect.right.coerceIn(x + 1, original.width)
        val bottom = rect.bottom.coerceIn(y + 1, original.height)
        val w = right - x
        val h = bottom - y
        if (w <= 0 || h <= 0) return original
        return Bitmap.createBitmap(original, x, y, w, h)
    }

    private fun finishWithA11yFallback(rect: Rect) {
        val roots = collectScanRoots()
        if (roots.isEmpty()) {
            Log.w(TAG, "Scan: no foreign window roots")
            broadcastScanResult("")
            return
        }

        val candidates = mutableListOf<String>()
        for (root in roots) {
            try {
                val hit = SmartPopupScanHelper.extractFromRegion(root, rect)
                if (!hit.isNullOrBlank()) candidates.add(hit)
            } finally {
                runCatching { root.recycle() }
            }
        }

        val best = candidates
            .distinct()
            .filter { SmartPopupScanHelper.isLikelyTrxId(it) }
            .maxWithOrNull(compareByDescending<String> { it.length.coerceAtMost(10) })

        Log.i(TAG, "A11y candidates=$candidates best='$best'")
        if (!best.isNullOrBlank()) vibrateSuccess()
        broadcastScanResult(best.orEmpty())
    }

    /**
     * Prefer other apps' windows (SMS/WhatsApp). Skip our own overlay package
     * so crop/bubble never becomes the scan target.
     */
    private fun collectScanRoots(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val wins: List<AccessibilityWindowInfo>? = try {
            windows
        } catch (_: Exception) {
            null
        }

        if (!wins.isNullOrEmpty()) {
            val sorted = wins.sortedByDescending { it.layer }
            for (w in sorted) {
                if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = try {
                    w.root
                } catch (_: Exception) {
                    null
                } ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg == packageName) {
                    runCatching { root.recycle() }
                    continue
                }
                out.add(root)
            }
        }

        if (out.isEmpty()) {
            val active = rootInActiveWindow
            if (active != null) {
                val pkg = active.packageName?.toString().orEmpty()
                if (pkg != packageName) out.add(active)
                else runCatching { active.recycle() }
            }
        }
        return out
    }

    private fun broadcastScanResult(query: String) {
        sendBroadcast(
            Intent(SmartPopupService.ACTION_SCAN_RESULT).apply {
                setPackage(packageName)
                putExtra(SmartPopupService.EXTRA_QUERY, query)
            }
        )
    }

    private fun vibrateSuccess() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    companion object {
        private const val TAG = "PaychekA11y"

        @Volatile
        var isRunning: Boolean = false
    }
}
