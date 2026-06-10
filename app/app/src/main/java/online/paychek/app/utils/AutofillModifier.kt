package online.paychek.app.utils

import android.os.Build
import android.view.View
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit
): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillNode = remember {
        AutofillNode(onFill = onFill, autofillTypes = autofillTypes)
    }
    LocalAutofillTree.current += autofillNode

    this.onGloballyPositioned {
        autofillNode.boundingBox = it.boundsInWindow()
    }.onFocusChanged { focusState ->
        autofill?.let {
            if (focusState.isFocused) {
                it.requestAutofillForNode(autofillNode)
            } else {
                it.cancelAutofillForNode(autofillNode)
            }
        }
    }
}

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
