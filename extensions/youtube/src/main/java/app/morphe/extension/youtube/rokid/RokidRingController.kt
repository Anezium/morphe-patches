/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewTreeObserver
import app.morphe.extension.shared.Logger
import java.lang.ref.WeakReference

/**
 * R08 ring support. While this activity is resumed it holds the ring focus of
 * R08 Access Bridge (the broadcast Nexus uses for its own surfaces), so the
 * bridge passes raw ring keys here instead of driving accessibility focus.
 * Ring keys become the temple keys the controller already handles, so every
 * surface (feed, sections, player, options, search) works the same way.
 */
object RokidRingController {
    private const val FOCUS_ACTION = "com.anezium.r08accessbridge.action.NEXUS_RING_FOCUS"
    private const val BRIDGE_PACKAGE = "com.anezium.r08accessbridge"

    /** The bridge drops a claim after 10 minutes; Nexus may also release it on its own changes. */
    private const val KEEPALIVE_MS = 60_000L
    private const val REFRESH_MIN_MS = 5_000L

    private val main = Handler(Looper.getMainLooper())
    private val input = RokidRingInput()
    private var activityRef = WeakReference<Activity>(null)
    private var callbacksRegistered = false
    private var claimed = false
    private var lastClaimMs = 0L

    private val keepalive = object : Runnable {
        override fun run() {
            if (!claimed) return
            publish(true)
            main.postDelayed(this, KEEPALIVE_MS)
        }
    }

    private val resolveTaps = Runnable {
        val activity = activityRef.get() ?: return@Runnable
        when (input.resolveExpired(SystemClock.uptimeMillis())) {
            RokidRingInput.Output.SELECT -> dispatch(activity, KeyEvent.KEYCODE_ENTER)
            RokidRingInput.Output.BACK -> dispatch(activity, KeyEvent.KEYCODE_BACK)
            else -> Unit
        }
    }

    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) claim()
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity === activityRef.get()) claim()
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity === activityRef.get()) release()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity === activityRef.get()) {
                release()
                activityRef = WeakReference(null)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    fun attach(activity: Activity) {
        if (activityRef.get() !== activity) {
            activityRef.get()?.window?.decorView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focusListener)
            activityRef = WeakReference(activity)
            activity.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusListener)
        }
        if (!callbacksRegistered) {
            activity.application.registerActivityLifecycleCallbacks(lifecycle)
            callbacksRegistered = true
        }
        if (activity.hasWindowFocus()) claim()
    }

    /** Re-asserts the claim now and then, since another app may have released it. */
    fun refresh() {
        if (claimed && SystemClock.uptimeMillis() - lastClaimMs >= REFRESH_MIN_MS) publish(true)
    }

    /**
     * Consumes every key of the ring and replays it as a temple key through
     * the activity, so the same Rokid rules, bypasses and logs apply.
     */
    fun handleKeyEvent(activity: Activity, event: KeyEvent): Boolean {
        if (!RokidRingInput.isRingKey(event.keyCode)) return false
        if (!RokidRingInput.isRingDevice(event.device?.name)) return false
        activityRef = WeakReference(activity)
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        Log.i("RokidControls", "ring key=${event.keyCode}")
        when (input.onKeyDown(event.keyCode, SystemClock.uptimeMillis())) {
            RokidRingInput.Output.NEXT -> dispatch(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            RokidRingInput.Output.PREVIOUS -> dispatch(activity, KeyEvent.KEYCODE_DPAD_LEFT)
            else -> Unit
        }
        main.removeCallbacks(resolveTaps)
        if (input.tapPending) main.postDelayed(resolveTaps, RokidRingInput.TAP_WINDOW_MS + 20L)
        return true
    }

    private fun dispatch(activity: Activity, keyCode: Int) {
        if (activity.isFinishing) return
        val now = SystemClock.uptimeMillis()
        try {
            activity.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            activity.dispatchKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        } catch (ex: Exception) {
            Logger.printException({ "Rokid ring dispatch failed" }, ex)
        }
    }

    private fun claim() {
        claimed = true
        publish(true)
        main.removeCallbacks(keepalive)
        main.postDelayed(keepalive, KEEPALIVE_MS)
    }

    private fun release() {
        main.removeCallbacks(keepalive)
        main.removeCallbacks(resolveTaps)
        input.reset()
        if (claimed) {
            claimed = false
            publish(false)
        }
    }

    private fun publish(focused: Boolean) {
        val context = activityRef.get()?.applicationContext ?: return
        lastClaimMs = SystemClock.uptimeMillis()
        try {
            context.sendBroadcast(
                Intent(FOCUS_ACTION)
                    .setPackage(BRIDGE_PACKAGE)
                    .putExtra("focused", focused)
                    .putExtra("ts", System.currentTimeMillis()),
            )
        } catch (ex: Exception) {
            Logger.printException({ "Rokid ring focus broadcast failed" }, ex)
        }
    }
}
