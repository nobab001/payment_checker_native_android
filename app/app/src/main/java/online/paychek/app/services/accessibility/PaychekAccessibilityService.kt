package online.paychek.app.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PaychekAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No action needed
    }

    override fun onInterrupt() {
        // No action needed
    }
}
