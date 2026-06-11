package online.paychek.app.utils

import android.os.Build
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView

fun Modifier.disableAutofill(): Modifier = composed {
    val view = LocalView.current
    this.onFocusChanged { focusState ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.importantForAutofill = if (focusState.isFocused) {
                View.IMPORTANT_FOR_AUTOFILL_NO
            } else {
                View.IMPORTANT_FOR_AUTOFILL_YES
            }
        }
    }
}
