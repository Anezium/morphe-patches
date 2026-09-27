/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * R08 ring keys as R08 Access Bridge passes them through while this app holds
 * the ring focus: slides are media next/previous, every tap is play/pause.
 * Taps are counted like the bridge and Nexus do: one tap selects, two go Back.
 */
class RokidRingInput(private val tapWindowMs: Long = TAP_WINDOW_MS) {
    enum class Output {
        NEXT,
        PREVIOUS,
        SELECT,
        BACK,
    }

    private var taps = 0
    private var lastTapMs = Long.MIN_VALUE

    /** A slide answers at once; a tap waits for [resolveExpired] after the window. */
    fun onKeyDown(keyCode: Int, timeMs: Long): Output? {
        return when (keyCode) {
            KEYCODE_MEDIA_NEXT -> {
                reset()
                Output.NEXT
            }
            KEYCODE_MEDIA_PREVIOUS -> {
                reset()
                Output.PREVIOUS
            }
            in TAP_KEYS -> {
                taps++
                lastTapMs = timeMs
                null
            }
            else -> null
        }
    }

    /** Taps resolve once the window has passed; three or more taps are dropped. */
    fun resolveExpired(timeMs: Long): Output? {
        if (taps == 0 || timeMs - lastTapMs < tapWindowMs) return null
        val output = when (taps) {
            1 -> Output.SELECT
            2 -> Output.BACK
            else -> null
        }
        reset()
        return output
    }

    val tapPending: Boolean get() = taps > 0

    fun reset() {
        taps = 0
        lastTapMs = Long.MIN_VALUE
    }

    companion object {
        const val TAP_WINDOW_MS = 350L
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85
        const val KEYCODE_MEDIA_NEXT = 87
        const val KEYCODE_MEDIA_PREVIOUS = 88
        const val KEYCODE_MEDIA_PLAY = 126
        const val KEYCODE_MEDIA_PAUSE = 127
        private val TAP_KEYS = setOf(KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_PLAY, KEYCODE_MEDIA_PAUSE)

        fun isRingKey(keyCode: Int): Boolean =
            keyCode == KEYCODE_MEDIA_NEXT || keyCode == KEYCODE_MEDIA_PREVIOUS || keyCode in TAP_KEYS

        /** The bridge recognizes the ring by its input device name. */
        fun isRingDevice(deviceName: String?): Boolean =
            deviceName != null && deviceName.uppercase().contains("R08")
    }
}
