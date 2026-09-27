/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidSection
import app.morphe.extension.youtube.rokid.RokidSectionsState
import org.junit.Assert.*
import org.junit.Test

class RokidSectionsStateTest {
    @Test fun openingAndMovingSelectsTheActivatedSection() {
        val state = RokidSectionsState()
        state.open(fromRoot = true)
        assertEquals(RokidSection.HOME, state.selected)
        state.move(1)
        assertEquals(RokidSection.SUBSCRIPTIONS, state.selected)
        assertTrue(state.isOpen)
    }

    @Test fun wrapIncludesRowsOutsideTheVisibleViewport() {
        val state = RokidSectionsState()
        state.open(fromRoot = true)
        state.move(-1)
        assertEquals(RokidSection.YOU, state.selected)
        state.move(1)
        assertEquals(RokidSection.HOME, state.selected)
        repeat(4) { state.move(1) }
        assertEquals(RokidSection.WATCH_LATER, state.selected)
    }

    @Test fun backRequestsAnActivityExitOnlyOnceAndOnlyFromRoot() {
        val state = RokidSectionsState()
        state.open(fromRoot = true)
        assertTrue(state.close())
        assertFalse(state.close())
        state.open(fromRoot = false)
        assertFalse(state.close())
        assertFalse(state.isOpen)
    }

    @Test fun closedSectionsIgnoreMovesAndReopenAtCurrentDestination() {
        val state = RokidSectionsState()
        state.move(1)
        assertEquals(0, state.index)
        state.open(fromRoot = false, selected = RokidSection.HISTORY)
        assertEquals(3, state.index)
        state.reset()
        assertFalse(state.isOpen)
        assertEquals(0, state.index)
    }

    @Test fun scrollingStartsAtTheFirstRowBelowTheViewport() {
        assertEquals(0, RokidSectionsState.scrollOffset(3, 117, 108, 520, 0))
        assertEquals(56, RokidSectionsState.scrollOffset(4, 117, 108, 520, 0))
        assertEquals(173, RokidSectionsState.scrollOffset(5, 117, 108, 520, 56))
        assertEquals(0, RokidSectionsState.scrollOffset(0, 117, 108, 520, 173))
    }

    @Test fun resizedViewportStillShowsTheSelectedRow() {
        assertEquals(273, RokidSectionsState.scrollOffset(5, 117, 108, 420, 173))
        assertEquals(234, RokidSectionsState.scrollOffset(2, 117, 108, 420, 273))
        assertEquals(0, RokidSectionsState.scrollOffset(5, 117, 108, 0, 273))
    }
}
