/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Crushes the video's near-black to true black. Only pure black is
 * transparent on the glasses optics; video "black" sits a few levels above it
 * with compression noise, which the optics show as a grainy film over the
 * whole picture. The transform applies to the video's own surface layer, so
 * YouTube's views and the glasses overlays are untouched.
 */
internal object RokidVideoBlackLevel {
    private var applied = WeakReference<SurfaceControl>(null)
    private var failed = false

    private val setColorTransform: Method? by lazy {
        try {
            // Hidden API: exempt only SurfaceControl, the class that carries it.
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/SurfaceControl")
            SurfaceControl.Transaction::class.java.getMethod(
                "setColorTransform",
                SurfaceControl::class.java,
                FloatArray::class.java,
                FloatArray::class.java,
            )
        } catch (ex: Exception) {
            Log.w("RokidControls", "video black level unavailable: $ex")
            null
        }
    }

    fun apply(player: View?) {
        if (failed || player == null) return
        val surface = findSurface(player) ?: return
        val control = surface.surfaceControl ?: return
        if (!control.isValid || applied.get() === control) return
        val method = setColorTransform ?: run { failed = true; return }
        try {
            val transaction = SurfaceControl.Transaction()
            method.invoke(transaction, control, MATRIX, TRANSLATION)
            transaction.apply()
            applied = WeakReference(control)
            Log.i("RokidControls", "video black level applied")
        } catch (ex: Exception) {
            failed = true
            Log.w("RokidControls", "video black level failed: $ex")
        }
    }

    private fun findSurface(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findSurface(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** out = (in - LIFT) * GAIN: levels up to LIFT (about 19 of 255) become black, white stays white. */
    private const val LIFT = 0.075f
    private const val GAIN = 1f / (1f - LIFT)
    private val MATRIX = floatArrayOf(GAIN, 0f, 0f, 0f, GAIN, 0f, 0f, 0f, GAIN)
    private val TRANSLATION = floatArrayOf(-LIFT * GAIN, -LIFT * GAIN, -LIFT * GAIN)
}
