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
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceUtils.getIdentifier
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.RokidControlsPatch.NativeControls
import java.lang.ref.WeakReference

/**
 * App-local play/pause: click the real player control, never a system media key.
 * YouTube owns lazy initialization of its controls and their listeners.
 */
object RokidPlayPauseController {
    private var nativeControls = WeakReference<NativeControls>(null)
    private var nativeRoot = WeakReference<View>(null)

    @JvmStatic
    fun bind(controls: NativeControls, root: View) {
        nativeControls = WeakReference(controls)
        nativeRoot = WeakReference(root)
    }

    private fun boundControls(activity: Activity): NativeControls? {
        val root = nativeRoot.get() ?: return null
        return if (root.isAttachedToWindow && root.rootView === activity.window.decorView) {
            nativeControls.get()
        } else null
    }

    fun isAttached(activity: Activity): Boolean {
        return findPlayPauseView(activity, log = false) != null
    }

    /**
     * True when a real button or the native initializer belongs to this activity.
     */
    fun canActivate(activity: Activity): Boolean {
        return isAttached(activity) || boundControls(activity) != null
    }

    fun isAvailable(activity: Activity): Boolean {
        return canActivate(activity)
    }

    fun tryPerformClick(activity: Activity): Boolean {
        Utils.verifyOnMainThread()
        boundControls(activity)?.patch_initializeControls()
        val view = findPlayPauseView(activity, log = true) ?: return false
        val hidden = revealForClick(view)
        return try {
            view.isShown && view.performClick()
        } finally {
            hidden.forEach { (node, visibility) -> node.visibility = visibility }
        }
    }

    /**
     * YouTube's pause listener ignores clicks unless the overlay is shown.
     * Unhide the attached chain for the click; do not tap the video surface.
     */
    private fun revealForClick(target: View): List<Pair<View, Int>> {
        if (target.isShown) {
            return emptyList()
        }
        val hidden = mutableListOf<Pair<View, Int>>()
        var current: View? = target
        while (current != null && !current.isShown) {
            hidden.add(current to current.visibility)
            val parent = current.parent
            current = parent as? View
            if (current != null && current.id == android.R.id.content) {
                break
            }
        }
        for (i in hidden.indices.reversed()) {
            val node = hidden[i].first
            if (node.visibility != View.VISIBLE) {
                node.visibility = View.VISIBLE
            }
        }
        return hidden
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
        val root = nativeRoot.get()?.takeIf { it.rootView === activity.window.decorView }
            ?: activity.window.decorView
        val parent = root.findViewById<ViewGroup>(parentId)
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
