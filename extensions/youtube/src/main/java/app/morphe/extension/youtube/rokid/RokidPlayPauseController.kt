/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceUtils.getIdentifier
import app.morphe.extension.shared.Utils

/**
 * App-local play/pause: click the real player control, never a system media key.
 * Auto-hidden overlay (GONE) is still a valid target. Missing/uninflated controls are not.
 */
object RokidPlayPauseController {
    fun isAttached(activity: Activity): Boolean {
        return findPlayPauseView(activity, log = false) != null
    }

    /**
     * True when the real play/pause control is attached, or the overlay stub
     * can still be inflated. VideoState alone is not a clickable target.
     */
    fun canActivate(activity: Activity): Boolean {
        return isAttached(activity) || hasInflatableStub(activity)
    }

    fun isAvailable(activity: Activity): Boolean {
        return canActivate(activity)
    }

    fun tryPerformClick(activity: Activity): Boolean {
        Utils.verifyOnMainThread()
        ensureControlsInflated(activity)
        val view = findPlayPauseView(activity, log = true) ?: return false
        revealForClick(view)
        if (!view.isShown) {
            Logger.printDebug { "Rokid play/pause: control still not shown after reveal" }
            return false
        }
        val clicked = view.performClick()
        if (!clicked) {
            Logger.printDebug { "Rokid play/pause: performClick returned false" }
        }
        return clicked
    }

    /**
     * YouTube's pause listener ignores clicks unless the overlay is shown.
     * Unhide the attached chain for the click; do not tap the video surface.
     */
    private fun revealForClick(target: View) {
        if (target.isShown) {
            return
        }
        val hidden = mutableListOf<View>()
        var current: View? = target
        while (current != null && !current.isShown) {
            hidden.add(current)
            val parent = current.parent
            current = parent as? View
            if (current != null && current.id == android.R.id.content) {
                break
            }
        }
        for (i in hidden.indices.reversed()) {
            val node = hidden[i]
            if (node.visibility != View.VISIBLE) {
                node.visibility = View.VISIBLE
            }
        }
        Logger.printDebug { "Rokid play/pause: revealed overlay for click isShown=${target.isShown}" }
    }

    /**
     * ViewStub is not inflated until the overlay has been shown once.
     * Do not tap the player surface to reveal it: that can toggle playback.
     */
    private fun hasInflatableStub(activity: Activity): Boolean {
        val stubId = resolveId(activity, "youtube_controls_button_group_layout_stub")
        if (stubId == 0) {
            return false
        }
        return activity.findViewById<View>(stubId) is ViewStub
    }

    private fun ensureControlsInflated(activity: Activity) {
        if (findPlayPauseView(activity, log = false) != null) {
            return
        }
        val stubId = resolveId(activity, "youtube_controls_button_group_layout_stub")
        if (stubId == 0) {
            return
        }
        val stub = activity.findViewById<View>(stubId) as? ViewStub ?: return
        try {
            stub.inflate()
            Logger.printDebug { "Rokid play/pause: inflated controls stub" }
        } catch (ex: Exception) {
            Logger.printException({ "Rokid play/pause: stub inflate failed" }, ex)
        }
    }

    /**
     * Bind the clickable ImageView under the known overlay parent.
     * The FrameLayout wrapper [player_control_play_pause_replay_button_touch_area]
     * is not clickable; performClick on it does not reach the control.
     * Parent scope avoids duplicate ids elsewhere in the tree.
     */
    private fun findPlayPauseView(activity: Activity, log: Boolean): View? {
        val parentId = resolveId(activity, "controls_button_group_layout")
        val buttonId = resolveId(activity, "player_control_play_pause_replay_button")
        if (parentId == 0 || buttonId == 0) {
            if (log) {
                Logger.printDebug { "Rokid play/pause: resource id missing" }
            }
            return null
        }
        val parent = activity.findViewById<ViewGroup>(parentId)
        if (parent == null) {
            if (log) {
                Logger.printDebug { "Rokid play/pause: controls_button_group_layout not attached" }
            }
            return null
        }
        val view = parent.findViewById<View>(buttonId)
        if (view == null || view.parent == null || !view.isAttachedToWindow) {
            if (log) {
                Logger.printDebug { "Rokid play/pause: button not attached under overlay parent" }
            }
            return null
        }
        if (!view.isEnabled) {
            if (log) {
                Logger.printDebug { "Rokid play/pause: button disabled" }
            }
            return null
        }
        if (!view.isClickable) {
            if (log) {
                Logger.printDebug { "Rokid play/pause: button not clickable" }
            }
            return null
        }
        if (log && (view.visibility != View.VISIBLE || !view.isShown)) {
            Logger.printDebug { "Rokid play/pause: overlay hidden, clicking attached control" }
        }
        return view
    }

    private fun resolveId(activity: Activity, name: String): Int {
        val fromContext = ResourceUtils.getIdentifier(ResourceType.ID, name)
        if (fromContext != 0) {
            return fromContext
        }
        return getIdentifier(activity, ResourceType.ID, name)
    }
}
