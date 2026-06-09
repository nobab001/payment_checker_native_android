package online.paychek.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import online.paychek.app.config.AppConfig
import online.paychek.app.ui.screen.auth.pin.SecurityGateScreen
import online.paychek.app.ui.theme.PaychekTheme
import online.paychek.app.utils.SecurePreferences

class MainActivity : FragmentActivity() {
    private var isAppLocked by mutableStateOf(false)
    private var wasStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Lock the app on start if a token exists and profile is complete
        val hasToken = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN).isNotEmpty()
        val isProfileComplete = SecurePreferences.decrypt(this, "pcu_profile_complete") != "false"
        if (hasToken && isProfileComplete) {
            isAppLocked = true
        }

        enableEdgeToEdge()
        setContent {
            PaychekTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigation()

                        if (isAppLocked) {
                            SecurityGateScreen(
                                onUnlockSuccess = {
                                    isAppLocked = false
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val hasToken = SecurePreferences.decrypt(this, AppConfig.KEY_AUTH_TOKEN).isNotEmpty()
        val isProfileComplete = SecurePreferences.decrypt(this, "pcu_profile_complete") != "false"
        if (hasToken && isProfileComplete && wasStopped) {
            isAppLocked = true
        }
        wasStopped = false
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }
}
