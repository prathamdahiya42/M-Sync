package com.classjam.core.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

object BrightnessHelper {
    fun setMaxBrightness(activity: Activity) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        activity.window.attributes = layoutParams
    }

    fun restoreBrightness(activity: Activity) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layoutParams
    }
}

/**
 * Composable effect to automatically force max screen brightness while in view
 * and restore original brightness when leaving.
 */
@Composable
fun ForceMaxBrightness() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(Unit) {
        BrightnessHelper.setMaxBrightness(activity)
        onDispose {
            BrightnessHelper.restoreBrightness(activity)
        }
    }
}
