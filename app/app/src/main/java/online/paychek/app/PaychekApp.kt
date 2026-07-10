package online.paychek.app

import android.app.Application
import android.util.Log
import online.paychek.app.data.remote.api.RetrofitClient
import online.paychek.app.services.connectivity.ConnectionEngine
import online.paychek.app.utils.SecurePreferences
import java.util.concurrent.Executors

/**
 * Application entry — keeps cold start light.
 * Heavy Keystore warm-up runs off the main thread so the first Activity paints fast.
 */
class PaychekApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        ConnectionEngine.init(this)

        Executors.newSingleThreadExecutor().execute {
            try {
                SecurePreferences.warmUp(this)
                online.paychek.app.data.local.prefs.PrefsHelper.migrateCachesFromSecureStoreIfNeeded(this)
            } catch (e: Exception) {
                Log.w(TAG, "Background warm-up failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PaychekApp"
    }
}
