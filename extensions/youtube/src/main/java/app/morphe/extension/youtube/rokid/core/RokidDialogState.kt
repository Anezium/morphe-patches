/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import java.lang.ref.WeakReference

class RokidDialogState<T : Any> {
    private val dialogs = mutableListOf<WeakReference<T>>()

    fun shown(dialog: T) {
        dismissed(dialog)
        dialogs.add(WeakReference(dialog))
    }

    fun dismissed(dialog: T) {
        dialogs.removeAll { it.get() == null || it.get() === dialog }
    }

    fun current(isShowingForActivity: (T) -> Boolean): T? = dialogs.asReversed().firstNotNullOfOrNull {
        // Dialog.isShowing changes on dismiss, before Android updates window focus on traversal.
        it.get()?.takeIf(isShowingForActivity)
    }
}
