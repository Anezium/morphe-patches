/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * Decide whether a Rokid-mapped key must pass through. Android-free so tests
 * can pin the rules without Views.
 *
 * Display TextViews (titles, timestamps, selectable overlay labels) are not
 * editors. [onCheckIsTextEditor] and [InputMethodManager.isActive] are not
 * inputs here: both fire on static player chrome and would leak DPAD into
 * YouTube's native seek handler.
 */
object RokidKeyBypass {
    enum class Reason {
        NONE,
        DESCRIPTION,
        EDITOR,
        DIALOG,
    }

    /**
     * InputType class bits. Same value as android.text.InputType.TYPE_MASK_CLASS.
     * Zero (TYPE_NULL) is display text, including selectable titles.
     */
    const val INPUT_TYPE_CLASS_MASK = 0x0000000f

    fun isEditableInputType(inputType: Int): Boolean {
        return (inputType and INPUT_TYPE_CLASS_MASK) != 0
    }

    fun classify(
        descriptionOpen: Boolean,
        editableField: Boolean,
        foreignWindow: Boolean,
    ): Reason {
        if (descriptionOpen) {
            return Reason.DESCRIPTION
        }
        if (editableField) {
            return Reason.EDITOR
        }
        if (foreignWindow) {
            return Reason.DIALOG
        }
        return Reason.NONE
    }
}
