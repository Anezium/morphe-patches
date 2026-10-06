/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import app.morphe.extension.youtube.patches.RokidControlsPatch

object RokidDialogController {
    private val dialogs = RokidDialogState<Dialog>()

    @JvmStatic
    fun markPositiveButton(button: Button) {
        button.id = android.R.id.button1
    }

    @JvmStatic
    fun prepare(pair: Pair<Dialog, LinearLayout>) {
        if (!RokidControlsPatch.isPatchIncluded()) return
        val dialog = pair.first
        val window = dialog.window ?: return
        val activity = findActivity(dialog.context) ?: return
        val original = window.callback ?: return
        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (RokidRingController.handleKeyEvent(activity, event)) return true
                return original.dispatchKeyEvent(event)
            }
        }
        window.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                dialogs.shown(dialog)
                dialog.findViewById<Button>(android.R.id.button1)?.apply {
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            }

            override fun onViewDetachedFromWindow(view: View) {
                dialogs.dismissed(dialog)
            }
        })
    }

    fun current(activity: Activity): Dialog? = dialogs.current {
        it.isShowing && findActivity(it.context) === activity
    }

    private fun findActivity(context: Context): Activity? {
        if (context is Activity) return context
        return if (context is ContextWrapper && context.baseContext !== context) findActivity(context.baseContext) else null
    }
}
