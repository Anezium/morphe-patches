/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidHint
import app.morphe.extension.youtube.rokid.RokidHudText
import app.morphe.extension.youtube.rokid.RokidSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RokidHudTextTest {
    @Test
    fun counterIsOneBasedWithTotal() {
        assertEquals("1 / 20", RokidHudText.counter(0, 20))
        assertEquals("20 / 20", RokidHudText.counter(19, 20))
    }

    @Test
    fun counterDropsAnUnknownOrInconsistentTotal() {
        assertEquals("3", RokidHudText.counter(2, -1))
        assertEquals("3", RokidHudText.counter(2, 2))
    }

    @Test
    fun counterIsHiddenWithoutPosition() {
        assertNull(RokidHudText.counter(-1, 20))
    }

    @Test
    fun feedHintsMatchTheMockup() {
        assertEquals(
            listOf(RokidHint("⇅", "next / prev"), RokidHint("●", "open"), RokidHint("◂", "sections")),
            RokidHudText.hints(RokidSurface.BROWSE),
        )
    }

    @Test
    fun playerHintsMatchTheMockup() {
        assertEquals(
            listOf(RokidHint("⇄", "move"), RokidHint("●", "press"), RokidHint("◂", "back to feed")),
            RokidHudText.hints(RokidSurface.PLAYER),
        )
    }

    @Test
    fun fullscreenHintsFollowPlayback() {
        assertEquals(
            listOf(RokidHint("●", "pause"), RokidHint("⇄", "seek 10 s"), RokidHint("◂", "leave")),
            RokidHudText.hints(RokidSurface.FULLSCREEN, playing = true),
        )
        assertEquals(RokidHint("●", "play"), RokidHudText.hints(RokidSurface.FULLSCREEN, playing = false)[0])
    }
}
