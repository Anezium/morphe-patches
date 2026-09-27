/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidCommand
import app.morphe.extension.youtube.rokid.RokidControlsState
import app.morphe.extension.youtube.rokid.RokidKeyBypass
import app.morphe.extension.youtube.rokid.RokidKeyMapper
import app.morphe.extension.youtube.rokid.RokidRailItem
import app.morphe.extension.youtube.rokid.RokidRailLabels
import app.morphe.extension.youtube.rokid.RokidSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RokidControlsStateTest {
    private val state = RokidControlsState()

    @Before
    fun setUp() {
        state.resetForTesting()
    }

    @Test
    fun browseBackDoesNotTrap() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.BACK, RokidSurface.BROWSE)
        assertEquals(RokidCommand.PASS_BACK, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun playerPhysicalBackPassesThrough() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.BACK, RokidSurface.PLAYER)
        assertEquals(RokidCommand.PASS_BACK, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun playerSelectOnBackRailDispatchesPlayerBack() {
        repeat(4) { state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER) }
        val dispatch = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.PLAYER_BACK, dispatch.command)
        assertEquals(RokidRailItem.BACK, dispatch.railItem)
        assertEquals(4, dispatch.railIndex)
        assertFalse(dispatch.consume)
    }

    @Test
    fun playPauseLabelFollowsAvailabilityAndVideoState() {
        assertEquals("Unavailable", RokidRailLabels.playPause(false, "PLAYING"))
        assertEquals("Pause", RokidRailLabels.playPause(true, "PLAYING"))
        assertEquals("Play", RokidRailLabels.playPause(true, "PAUSED"))
        assertEquals("Unavailable", RokidRailLabels.playPause(false, "PAUSED"))
        assertEquals("Play", RokidRailLabels.playPause(true, null))
        assertEquals("Unavailable", RokidRailLabels.playPause(false, null))
        assertEquals("Error", RokidRailLabels.playPause(true, "UNRECOVERABLE_ERROR"))
        assertEquals("Error", RokidRailLabels.playPause(true, "RECOVERABLE_ERROR"))
        assertEquals("Unavailable", RokidRailLabels.playPause(false, "UNRECOVERABLE_ERROR"))
    }

    @Test
    fun descriptionPanelHidesPlayerRail() {
        assertTrue(RokidRailLabels.hidePlayerRailForDescription(true))
        assertFalse(RokidRailLabels.hidePlayerRailForDescription(false))
    }

    @Test
    fun selectableDisplayTextDoesNotBypassKeys() {
        assertFalse(RokidKeyBypass.isEditableInputType(0))
        assertTrue(RokidKeyBypass.isEditableInputType(1))
        assertEquals(
            RokidKeyBypass.Reason.NONE,
            RokidKeyBypass.classify(
                descriptionOpen = false,
                editableField = false,
                foreignWindow = false,
            ),
        )
        assertEquals(
            RokidKeyBypass.Reason.EDITOR,
            RokidKeyBypass.classify(
                descriptionOpen = false,
                editableField = true,
                foreignWindow = false,
            ),
        )
        assertEquals(
            RokidKeyBypass.Reason.DIALOG,
            RokidKeyBypass.classify(
                descriptionOpen = false,
                editableField = false,
                foreignWindow = true,
            ),
        )
        assertEquals(
            RokidKeyBypass.Reason.DESCRIPTION,
            RokidKeyBypass.classify(
                descriptionOpen = true,
                editableField = true,
                foreignWindow = true,
            ),
        )
    }

    @Test
    fun cachedTimeIsIdentityBoundAndResets() {
        val empty = RokidRailLabels.resetPlaybackCache()
        val refusedBeforeBind = RokidRailLabels.playbackCacheAfterTimeSample(
            empty,
            "vidA",
            12_000L,
        )
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(refusedBeforeBind, "vidA"))

        val bound = RokidRailLabels.playbackCacheAfterVideoId(empty, "vidA")
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(bound, "vidA"))
        val sampled = RokidRailLabels.playbackCacheAfterTimeSample(bound, "vidA", 12_000L)
        assertEquals(12_000L, RokidRailLabels.identityBoundCachedTime(sampled, "vidA"))
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(sampled, "vidB"))
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(sampled, ""))
        assertTrue(RokidRailLabels.identityBoundCachedTime(sampled, "vidA") >= 0L)
        assertFalse(RokidRailLabels.seekAvailable(60_000L, -1L))

        val refusedForeign = RokidRailLabels.playbackCacheAfterTimeSample(sampled, "vidB", 250L)
        assertEquals(12_000L, RokidRailLabels.identityBoundCachedTime(refusedForeign, "vidA"))
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(refusedForeign, "vidB"))

        val switched = RokidRailLabels.playbackCacheAfterVideoId(sampled, "vidB")
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(switched, "vidB"))
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(switched, "vidA"))

        val sameIdKeepsTime = RokidRailLabels.playbackCacheAfterVideoId(sampled, "vidA")
        assertEquals(12_000L, RokidRailLabels.identityBoundCachedTime(sameIdKeepsTime, "vidA"))

        val afterInit = RokidRailLabels.resetPlaybackCache()
        assertEquals(-1L, RokidRailLabels.identityBoundCachedTime(afterInit, "vidA"))
        assertEquals(
            -1L,
            RokidRailLabels.identityBoundCachedTime(
                RokidRailLabels.playbackCacheAfterVideoId(sampled, ""),
                "",
            ),
        )
    }

    @Test
    fun seekHelpersClampAndRejectUnknownDuration() {
        assertFalse(RokidRailLabels.seekAvailable(0L, 0L))
        assertFalse(RokidRailLabels.seekAvailable(60_000L, -1L))
        assertTrue(RokidRailLabels.seekAvailable(60_000L, 0L))
        assertEquals(5_000L, RokidRailLabels.resolvePlaybackTime(5_000L, 1_000L))
        assertEquals(1_000L, RokidRailLabels.resolvePlaybackTime(-1L, 1_000L))
        assertEquals(-1L, RokidRailLabels.resolvePlaybackTime(-1L, -1L))
        assertEquals(15_000L, RokidRailLabels.clampSeekTarget(5_000L, 10_000L, 60_000L))
        assertEquals(0L, RokidRailLabels.clampSeekTarget(5_000L, -10_000L, 60_000L))
        assertEquals(60_000L, RokidRailLabels.clampSeekTarget(55_000L, 10_000L, 60_000L))
        assertEquals("-10s", RokidRailLabels.seek(backward = true, available = true))
        assertEquals("+10s", RokidRailLabels.seek(backward = false, available = true))
        assertEquals("Unavailable", RokidRailLabels.seek(backward = true, available = false))
    }

    @Test
    fun playerSwipeMovesOneRailItem() {
        val first = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.RAIL_MOVED, first.command)
        assertTrue(first.consume)
        assertEquals(RokidRailItem.SEEK_BACK, first.railItem)
        assertEquals(1, first.railIndex)

        val second = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        assertEquals(RokidRailItem.SEEK_FORWARD, second.railItem)
    }

    @Test
    fun playerSelectRequestsActivationWithoutPreConsume() {
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        val dispatch = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.ACTIVATE_SEEK_BACK, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun duplicateIsConsumedWithoutMovingRail() {
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        val duplicate = state.dispatch(RokidKeyMapper.Action.DUPLICATE, RokidSurface.PLAYER)
        assertEquals(RokidCommand.CONSUME, duplicate.command)
        assertTrue(duplicate.consume)
        assertEquals(1, duplicate.railIndex)
    }

    @Test
    fun syncSurfaceResetsRailWithoutAKey() {
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        assertEquals(1, state.railIndex)
        state.syncSurface(RokidSurface.BROWSE)
        state.syncSurface(RokidSurface.PLAYER)
        assertEquals(0, state.railIndex)
    }

    @Test
    fun enteringPlayerResetsRailToPlayPause() {
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.BROWSE)
        val dispatch = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.ACTIVATE_PLAY_PAUSE, dispatch.command)
        assertEquals(0, dispatch.railIndex)
        assertFalse(dispatch.consume)
    }

    @Test
    fun browseSelectDoesNotPreConsume() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.BROWSE)
        assertEquals(RokidCommand.FEED_SELECT, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun browseStepDoesNotPreConsume() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.BROWSE)
        assertEquals(RokidCommand.FEED_NEXT, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun fullscreenSwipesSeekWithoutMovingTheRail() {
        repeat(3) { state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER) }
        val forward = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.FULLSCREEN)
        assertEquals(RokidCommand.ACTIVATE_SEEK_FORWARD, forward.command)
        assertEquals(RokidRailItem.SEEK_FORWARD, forward.railItem)
        assertFalse(forward.consume)
        val back = state.dispatch(RokidKeyMapper.Action.PREVIOUS, RokidSurface.FULLSCREEN)
        assertEquals(RokidCommand.ACTIVATE_SEEK_BACK, back.command)
        assertEquals(3, state.railIndex)
    }

    @Test
    fun fullscreenTapTogglesPlayback() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.FULLSCREEN)
        assertEquals(RokidCommand.ACTIVATE_PLAY_PAUSE, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun fullscreenBackPassesThrough() {
        val dispatch = state.dispatch(RokidKeyMapper.Action.BACK, RokidSurface.FULLSCREEN)
        assertEquals(RokidCommand.PASS_BACK, dispatch.command)
        assertFalse(dispatch.consume)
    }

    @Test
    fun leavingFullscreenKeepsTheFullscreenKey() {
        repeat(3) { state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER) }
        state.syncSurface(RokidSurface.FULLSCREEN)
        state.syncSurface(RokidSurface.PLAYER)
        assertEquals(3, state.railIndex)
        state.syncSurface(RokidSurface.BROWSE)
        state.syncSurface(RokidSurface.PLAYER)
        assertEquals(0, state.railIndex)
    }

    @Test
    fun hiddenRailKeyOnlyRevealsIt() {
        state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        state.hideRail()
        assertTrue(state.railHidden)
        val reveal = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.RAIL_REVEAL, reveal.command)
        assertTrue(reveal.consume)
        assertEquals(1, reveal.railIndex)
        assertFalse(state.railHidden)
        val move = state.dispatch(RokidKeyMapper.Action.NEXT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.RAIL_MOVED, move.command)
        assertEquals(2, move.railIndex)
    }

    @Test
    fun hiddenRailSelectDoesNotActivate() {
        state.syncSurface(RokidSurface.PLAYER)
        state.hideRail()
        val reveal = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.RAIL_REVEAL, reveal.command)
        val press = state.dispatch(RokidKeyMapper.Action.SELECT, RokidSurface.PLAYER)
        assertEquals(RokidCommand.ACTIVATE_PLAY_PAUSE, press.command)
    }

    @Test
    fun hiddenRailStillLetsBackThrough() {
        state.syncSurface(RokidSurface.PLAYER)
        state.hideRail()
        val dispatch = state.dispatch(RokidKeyMapper.Action.BACK, RokidSurface.PLAYER)
        assertEquals(RokidCommand.PASS_BACK, dispatch.command)
        assertFalse(dispatch.consume)
        assertTrue(state.railHidden)
    }

    @Test
    fun onlyThePlayerRailHides() {
        state.syncSurface(RokidSurface.BROWSE)
        state.hideRail()
        assertFalse(state.railHidden)
        state.syncSurface(RokidSurface.PLAYER)
        state.hideRail()
        state.syncSurface(RokidSurface.FULLSCREEN)
        assertFalse(state.railHidden)
    }

    @Test
    fun clockAndSeekBarHelpers() {
        assertEquals("12:04", RokidRailLabels.clock(724_000L))
        assertEquals("0:05", RokidRailLabels.clock(5_900L))
        assertEquals("1:02:03", RokidRailLabels.clock(3_723_000L))
        assertEquals("--:--", RokidRailLabels.clock(-1L))
        assertEquals("12:04 / 30:23", RokidRailLabels.timeLabel(724_000L, 1_823_000L))
        assertEquals("0:10", RokidRailLabels.timeLabel(10_000L, 0L))
        assertEquals(0.5f, RokidRailLabels.playedFraction(30_000L, 60_000L), 0.0001f)
        assertEquals(1f, RokidRailLabels.playedFraction(90_000L, 60_000L), 0.0001f)
        assertEquals(0f, RokidRailLabels.playedFraction(-1L, 60_000L), 0.0001f)
        assertEquals(0f, RokidRailLabels.playedFraction(30_000L, 0L), 0.0001f)
    }
}
