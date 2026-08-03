package online.paychek.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryExemptionIntentClassifierTest {

    @Test
    fun aospRequestIgnoreDialog_isAccepted() {
        assertTrue(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.android.settings.fuelgauge.RequestIgnoreBatteryOptimizations"
            )
        )
    }

    @Test
    fun appBatteryUsage_isRejected() {
        assertFalse(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.android.settings.fuelgauge.AppBatteryUsageActivity"
            )
        )
    }

    @Test
    fun advancedPowerUsageDetail_isRejected() {
        assertFalse(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.android.settings.fuelgauge.AdvancedPowerUsageDetail"
            )
        )
    }

    @Test
    fun backgroundUsagePage_isRejected() {
        assertFalse(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.android.settings.applications.manageapplications.BackgroundUsageActivity"
            )
        )
    }

    @Test
    fun samsungSleepingAppsList_isRejected() {
        assertFalse(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
            )
        )
    }

    @Test
    fun unknownSettingsActivity_allowedOnce() {
        assertTrue(
            BatteryExemptionIntentClassifier.looksLikeSystemExemptionDialog(
                "com.android.settings.Settings\$SomeBatteryDialog"
            )
        )
    }
}
