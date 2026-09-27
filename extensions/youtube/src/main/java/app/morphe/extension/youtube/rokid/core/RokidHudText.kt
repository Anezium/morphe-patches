/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * One entry of the bottom hints strip: a gesture glyph and what it does here.
 */
data class RokidHint(val glyph: String, val label: String)

/**
 * Text of the glasses HUD, per surface, as in the glasses mode mockup.
 */
object RokidHudText {
    fun hints(surface: RokidSurface): List<RokidHint> {
        return when (surface) {
            RokidSurface.BROWSE -> listOf(
                RokidHint("⇅", "next / prev"),
                RokidHint("●", "open"),
                RokidHint("◂", "sections"),
            )
            RokidSurface.PLAYER -> listOf(
                RokidHint("⇄", "move"),
                RokidHint("●", "press"),
                RokidHint("◂", "back to feed"),
            )
        }
    }

    /**
     * One-based "n / N" counter. Unknown total shows the position alone;
     * unknown position shows nothing.
     */
    fun counter(zeroBasedPosition: Int, itemCount: Int): String? {
        if (zeroBasedPosition < 0) {
            return null
        }
        val position = zeroBasedPosition + 1
        return if (itemCount >= position) "$position / $itemCount" else "$position"
    }
}
