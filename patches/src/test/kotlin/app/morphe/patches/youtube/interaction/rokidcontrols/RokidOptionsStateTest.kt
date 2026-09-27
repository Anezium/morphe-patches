/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidCaptionChoice
import app.morphe.extension.youtube.rokid.RokidCaptionChoices
import app.morphe.extension.youtube.rokid.RokidHint
import app.morphe.extension.youtube.rokid.RokidHudText
import app.morphe.extension.youtube.rokid.RokidOption
import app.morphe.extension.youtube.rokid.RokidOptionsPage
import app.morphe.extension.youtube.rokid.RokidOptionsState
import app.morphe.extension.youtube.rokid.RokidPlaybackSpeeds
import app.morphe.extension.youtube.rokid.RokidSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RokidOptionsStateTest {
    private val state = RokidOptionsState()

    private val off = RokidCaptionChoice("Off", off = true)
    private val english = RokidCaptionChoice("English", off = false)
    private val french = RokidCaptionChoice("French (auto-generated)", off = false)
    private val menu = listOf(off, english, french)

    @Test
    fun openingStartsOnSpeed() {
        state.open()
        assertTrue(state.isOpen)
        assertEquals(RokidOptionsPage.MAIN, state.page)
        assertEquals(RokidOption.SPEED, state.selectedOption)
    }

    @Test
    fun moveWrapsInsideThePage() {
        state.open()
        state.move(-1, 3)
        assertEquals(RokidOption.LANGUAGE, state.selectedOption)
        state.move(1, 3)
        assertEquals(0, state.index)
    }

    @Test
    fun backFromASubListReturnsToItsRow() {
        state.open()
        state.enter(RokidOptionsPage.SPEED, 4)
        assertEquals(4, state.index)
        state.back()
        assertEquals(RokidOptionsPage.MAIN, state.page)
        assertEquals(RokidOption.SPEED, state.selectedOption)
        state.enter(RokidOptionsPage.LANGUAGE, 2)
        state.back()
        assertEquals(RokidOption.LANGUAGE, state.selectedOption)
        assertTrue(state.isOpen)
    }

    @Test
    fun backFromTheMainListCloses() {
        state.open()
        state.back()
        assertFalse(state.isOpen)
    }

    @Test
    fun keysDoNothingWhileClosed() {
        state.move(1, 3)
        state.enter(RokidOptionsPage.SPEED, 2)
        assertEquals(0, state.index)
        assertEquals(RokidOptionsPage.MAIN, state.page)
    }

    @Test
    fun speedLabelsAndNearestRow() {
        assertEquals("1×", RokidPlaybackSpeeds.label(1f))
        assertEquals("1.25×", RokidPlaybackSpeeds.label(1.25f))
        assertEquals("0.5×", RokidPlaybackSpeeds.label(0.5f))
        assertEquals(RokidPlaybackSpeeds.values.indexOf(1f), RokidPlaybackSpeeds.nearestIndex(1f))
        assertEquals(RokidPlaybackSpeeds.values.indexOf(2f), RokidPlaybackSpeeds.nearestIndex(3f))
        assertEquals(RokidPlaybackSpeeds.values.indexOf(1.25f), RokidPlaybackSpeeds.nearestIndex(1.3f))
    }

    @Test
    fun captionsCurrentFallsBackToOff() {
        assertEquals(2, RokidCaptionChoices.currentIndex(menu, 2))
        assertEquals(0, RokidCaptionChoices.currentIndex(menu, -1))
        assertTrue(RokidCaptionChoices.isOn(menu, 1))
        assertFalse(RokidCaptionChoices.isOn(menu, 0))
    }

    @Test
    fun captionsToggleGoesOffThenBackToTheLastTrack() {
        assertEquals(0, RokidCaptionChoices.toggleTarget(menu, 2, lastOnLabel = null))
        assertEquals(2, RokidCaptionChoices.toggleTarget(menu, 0, lastOnLabel = french.label))
        assertEquals(1, RokidCaptionChoices.toggleTarget(menu, 0, lastOnLabel = null))
        assertNull(RokidCaptionChoices.toggleTarget(listOf(off), 0, lastOnLabel = null))
    }

    @Test
    fun languageSummaryNamesTheShownTrack() {
        assertEquals("French (auto-generated)", RokidCaptionChoices.languageSummary(menu, 2))
        assertEquals("Off", RokidCaptionChoices.languageSummary(menu, 0))
        assertEquals("None", RokidCaptionChoices.languageSummary(listOf(off), 0))
        assertEquals("None", RokidCaptionChoices.languageSummary(emptyList(), -1))
    }

    @Test
    fun optionsHints() {
        assertEquals(
            listOf(RokidHint("⇅", "choose"), RokidHint("●", "set"), RokidHint("◂", "close")),
            RokidHudText.hints(RokidSurface.OPTIONS),
        )
    }
}
