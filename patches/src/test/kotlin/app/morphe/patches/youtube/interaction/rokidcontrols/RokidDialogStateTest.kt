/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidDialogState
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RokidDialogStateTest {
    private class Dialog(val owner: Any, var showing: Boolean = true)

    @Test
    fun dismissedDialogStopsOwningKeysBeforeDetachOrWindowFocusTraversal() {
        val state = RokidDialogState<Dialog>()
        val activity = Any()
        val dialog = Dialog(activity)
        state.shown(dialog)
        assertSame(dialog, state.current { it.showing && it.owner === activity })

        dialog.showing = false
        assertNull(state.current { it.showing && it.owner === activity })
        state.dismissed(dialog)
        assertNull(state.current { it.showing && it.owner === activity })
    }

    @Test
    fun onlyTheNewestShowingDialogForTheActivityOwnsKeys() {
        val state = RokidDialogState<Dialog>()
        val activity = Any()
        val first = Dialog(activity)
        val second = Dialog(activity)
        val other = Dialog(Any())
        state.shown(first)
        state.shown(second)
        state.shown(other)
        assertSame(second, state.current { it.showing && it.owner === activity })
        state.dismissed(second)
        assertSame(first, state.current { it.showing && it.owner === activity })
        state.dismissed(first)
        assertNull(state.current { it.showing && it.owner === activity })
        assertSame(other, state.current { it.showing && it.owner === other.owner })
    }

    @Test
    fun reshownDialogIsRegisteredOnceAndDismissResumesControls() {
        val state = RokidDialogState<Dialog>()
        val dialog = Dialog(Any())
        state.shown(dialog)
        state.shown(dialog)
        state.dismissed(dialog)
        assertNull(state.current { it.showing })
    }
}
