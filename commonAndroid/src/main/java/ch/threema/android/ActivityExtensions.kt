package ch.threema.android

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.app.Activity.OVERRIDE_TRANSITION_OPEN
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.InsetsType

fun Activity.findRootView(): View? {
    var rootView: View? = window.decorView.rootView
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        try {
            val decorView = window.decorView as ViewGroup
            if (decorView.childCount == 1) {
                rootView = decorView.getChildAt(0)
            }
        } catch (_: Exception) {
            rootView = null
        }
    }
    return rootView
}

/**
 *  @return The insets of this activities root view at the current time. If the root view or its insets could not be determined, [Insets.NONE] is returned.
 */
fun Activity.getCurrentInsets(@InsetsType types: Int): Insets {
    val rootView: View = findRootView() ?: return Insets.NONE
    val windowInsetsCompat: WindowInsetsCompat = ViewCompat.getRootWindowInsets(rootView) ?: return Insets.NONE
    return windowInsetsCompat.getInsets(types)
}

val Activity.context: Context
    get() = this

fun Activity.disableEnterTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0, Color.TRANSPARENT)
    } else {
        overridePendingTransition(0, 0)
    }
}

fun Activity.disableExitTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0, Color.TRANSPARENT)
    } else {
        overridePendingTransition(0, 0)
    }
}
