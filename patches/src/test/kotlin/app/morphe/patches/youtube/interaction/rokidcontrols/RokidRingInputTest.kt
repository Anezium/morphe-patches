/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidRingInput
import app.morphe.extension.youtube.rokid.RokidRingInput.Output
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RokidRingInputTest {
    private val ring = RokidRingInput(tapWindowMs = 350L)

    @Test
    fun slidesAnswerAtOnce() {
        assertEquals(Output.NEXT, ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_NEXT, 0L))
        assertEquals(Output.PREVIOUS, ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PREVIOUS, 10L))
    }

    @Test
    fun oneTapSelectsAfterTheWindow() {
        assertNull(ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PLAY_PAUSE, 0L))
        assertTrue(ring.tapPending)
        assertNull(ring.resolveExpired(200L))
        assertEquals(Output.SELECT, ring.resolveExpired(360L))
        assertFalse(ring.tapPending)
    }

    @Test
    fun twoTapsGoBack() {
        ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PLAY_PAUSE, 0L)
        ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PLAY_PAUSE, 200L)
        assertNull(ring.resolveExpired(400L))
        assertEquals(Output.BACK, ring.resolveExpired(560L))
    }

    @Test
    fun threeTapsAreDropped() {
        repeat(3) { ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PLAY_PAUSE, it * 100L) }
        assertNull(ring.resolveExpired(1_000L))
        assertFalse(ring.tapPending)
    }

    @Test
    fun aSlideCancelsPendingTaps() {
        ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_PLAY_PAUSE, 0L)
        assertEquals(Output.NEXT, ring.onKeyDown(RokidRingInput.KEYCODE_MEDIA_NEXT, 100L))
        assertNull(ring.resolveExpired(1_000L))
    }

    @Test
    fun onlyTheRingIsRecognized() {
        assertTrue(RokidRingInput.isRingDevice("R08 Ring"))
        assertTrue(RokidRingInput.isRingDevice("r08-1234"))
        assertFalse(RokidRingInput.isRingDevice("Galaxy Buds"))
        assertFalse(RokidRingInput.isRingDevice(null))
        assertTrue(RokidRingInput.isRingKey(RokidRingInput.KEYCODE_MEDIA_PLAY))
        assertFalse(RokidRingInput.isRingKey(66))
    }
}
