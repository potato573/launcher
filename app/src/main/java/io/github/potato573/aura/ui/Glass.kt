package io.github.potato573.aura.ui

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import io.github.potato573.aura.data.Settings
import io.github.potato573.aura.util.dp
import java.util.function.Consumer

/**
 * Real background blur through the public window-blur API (Android 12+): the system
 * compositor blurs whatever is behind the window (for AURA, the wallpaper). There is no public
 * API to blur only the area behind one view, so the whole wallpaper blurs while a panel is
 * open, the way the system shade does. Devices or modes without cross-window blur (older
 * Android, battery saver, some GPUs) get more opaque glass instead, so text stays readable.
 */
object Glass {

    /** True when the system will actually draw window blur right now. */
    fun isBlurAvailable(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        return try {
            activity.windowManager.isCrossWindowBlurEnabled
        } catch (e: RuntimeException) {
            false
        }
    }

    /** Whether blur is both available and wanted by the user. */
    fun isBlurOn(activity: Activity, settings: Settings): Boolean =
        settings.blurStrength > 0 && isBlurAvailable(activity)

    /** Blur radius in pixels at full strength of the user's setting. */
    fun maxRadius(activity: Activity, settings: Settings): Int =
        activity.dp(MAX_RADIUS_DP) * settings.blurStrength / 100

    /**
     * Calls [onChange] when blur becomes available or unavailable (e.g. battery saver turns
     * it off). Returns a function that removes the listener; no-op below Android 12.
     */
    fun watchAvailability(activity: Activity, onChange: (Boolean) -> Unit): () -> Unit {
        if (Build.VERSION.SDK_INT < 31) return {}
        val wm = activity.windowManager
        var first = true
        val listener = Consumer<Boolean> { enabled ->
            // The listener is called once immediately with the current value: skip that one.
            if (first) first = false else onChange(enabled)
        }
        try {
            wm.addCrossWindowBlurEnabledListener(activity.mainExecutor, listener)
        } catch (e: RuntimeException) {
            return {}
        }
        return {
            try {
                wm.removeCrossWindowBlurEnabledListener(listener)
            } catch (e: RuntimeException) {
                // Window manager already gone.
            }
        }
    }

    /** Sets the blur behind the activity's window. Cheap to call per frame (radius only). */
    fun setWindowBlur(activity: Activity, radius: Int) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            activity.window.setBackgroundBlurRadius(radius.coerceAtLeast(0))
        } catch (e: RuntimeException) {
            // Window not ready or blur unsupported: the tinted glass still shows.
        }
    }

    /** Blurs a view's own content (used for the home icons behind an open folder). */
    fun setViewBlur(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT < 31) return
        view.setRenderEffect(
            if (radius < 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
    }

    private const val MAX_RADIUS_DP = 40
}
