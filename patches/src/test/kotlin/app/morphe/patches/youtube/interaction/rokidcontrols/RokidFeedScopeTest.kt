/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidFeedScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RokidFeedScopeTest {
    @Test
    fun containerOrderPrefersResults() {
        assertEquals(
            listOf("results", "loading_layout", "pane_fragment_contents"),
            RokidFeedScope.containerNames,
        )
        assertTrue(RokidFeedScope.isResultsContainer("results"))
        assertFalse(RokidFeedScope.isResultsContainer("pivot_bar"))
    }

    @Test
    fun navbarToolbarAndMiniplayerAreChrome() {
        assertTrue(RokidFeedScope.isChrome("pivot_bar"))
        assertTrue(RokidFeedScope.isChrome("bottom_bar_container"))
        assertTrue(RokidFeedScope.isChrome("toolbar"))
        assertTrue(RokidFeedScope.isChrome("toolbar_container"))
        assertTrue(RokidFeedScope.isChrome("appbar_layout"))
        assertTrue(RokidFeedScope.isChrome("floaty_bar_controls_view"))
        assertTrue(RokidFeedScope.isChrome("modern_miniplayer_overlay_action_button"))
        assertFalse(RokidFeedScope.isChrome("results"))
        assertFalse(RokidFeedScope.isChrome("loading_layout"))
    }

    @Test
    fun lithoCardPreferredOverChip() {
        val item = 480 * 414
        val lithoCard = 480 * 378
        val avatar = 54 * 54
        val overflow = 36 * 36
        assertTrue(RokidFeedScope.preferLargeClickable(item, lithoCard))
        assertFalse(RokidFeedScope.preferLargeClickable(item, avatar))
        assertFalse(RokidFeedScope.preferLargeClickable(item, overflow))
        assertFalse(RokidFeedScope.preferLargeClickable(0, lithoCard))
    }
}
