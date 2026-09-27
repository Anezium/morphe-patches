/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * One-axis Rokid key mapper.
 *
 * All directional aliases share one monotonic debounce window.
 * Key-repeat DUPLICATE applies only to handled direction and select keys.
 * Unknown keys (including volume) and Back always return NONE/BACK so the
 * normal Activity path can run. Keycode integers match `android.view.KeyEvent`.
 * SPACE/ENTER/CENTER still classify as SELECT; editor/dialog passthrough is
 * decided at runtime by RokidControlsController, not here.
 */
object RokidKeyMapper {
    const val DIRECTION_DEBOUNCE_MS = 240L

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1

    const val KEYCODE_BACK = 4
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_NUMPAD_ENTER = 160
    const val KEYCODE_ROKID_SWIPE_FORWARD = 183
    const val KEYCODE_ROKID_SWIPE_BACK = 184
    const val KEYCODE_ROKID_DOUBLE_TAP = 202

    enum class Action {
        NONE,
        DUPLICATE,
        PREVIOUS,
        NEXT,
        SELECT,
        BACK,
    }

    @Volatile
    private var lastDirectionAt = 0L

    fun map(eventAction: Int, keyCode: Int, repeatCount: Int, now: Long): Action {
        if (eventAction != ACTION_DOWN) {
            return Action.NONE
        }
        val classified = classify(keyCode)
        if (classified == Action.NONE) {
            return Action.NONE
        }
        if (classified == Action.BACK) {
            return if (repeatCount > 0) Action.NONE else Action.BACK
        }
        if (repeatCount > 0) {
            return Action.DUPLICATE
        }
        return if (classified == Action.SELECT) {
            Action.SELECT
        } else {
            debounce(classified, now)
        }
    }

    fun mapKey(keyCode: Int, now: Long): Action {
        return map(ACTION_DOWN, keyCode, 0, now)
    }

    fun resetDebounce() {
        lastDirectionAt = 0L
    }

    fun resetDebounceForTesting() {
        resetDebounce()
    }

    /**
     * Direction and select keys YouTube's player also handles as seek/activate.
     * Back is excluded: it must keep the normal Activity path.
     */
    fun isDirectionOrSelect(keyCode: Int): Boolean {
        val classified = classify(keyCode)
        return classified == Action.PREVIOUS ||
            classified == Action.NEXT ||
            classified == Action.SELECT
    }

    /**
     * Keys this mapper treats as Rokid navigation or control.
     * Letter/digit/symbol keycodes are excluded so they are never logged.
     */
    fun isRokidKey(keyCode: Int): Boolean {
        return classify(keyCode) != Action.NONE
    }

    private fun classify(keyCode: Int): Action {
        return when (keyCode) {
            KEYCODE_DPAD_LEFT, KEYCODE_DPAD_UP, KEYCODE_ROKID_SWIPE_BACK -> Action.PREVIOUS
            KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_DOWN, KEYCODE_ROKID_SWIPE_FORWARD -> Action.NEXT
            KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER, KEYCODE_SPACE,
            KEYCODE_ROKID_DOUBLE_TAP -> Action.SELECT
            KEYCODE_BACK -> Action.BACK
            else -> Action.NONE
        }
    }

    private fun debounce(action: Action, now: Long): Action {
        if (lastDirectionAt > 0L && now - lastDirectionAt < DIRECTION_DEBOUNCE_MS) {
            return Action.DUPLICATE
        }
        lastDirectionAt = now
        return action
    }
}
