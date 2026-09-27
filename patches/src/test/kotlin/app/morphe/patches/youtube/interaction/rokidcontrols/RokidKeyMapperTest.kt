/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidKeyMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RokidKeyMapperTest {
    @Before
    fun setUp() {
        RokidKeyMapper.resetDebounceForTesting()
    }

    @Test
    fun actionUpIsIgnored() {
        assertEquals(
            RokidKeyMapper.Action.NONE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_UP,
                RokidKeyMapper.KEYCODE_DPAD_RIGHT,
                0,
                1_000L,
            ),
        )
    }

    @Test
    fun heldDirectionalKeyIsDuplicate() {
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_DPAD_RIGHT,
                1,
                1_000L,
            ),
        )
    }

    @Test
    fun heldSelectIsDuplicate() {
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_DPAD_CENTER,
                1,
                1_000L,
            ),
        )
    }

    @Test
    fun unknownRepeatedVolumeKeyPassesThrough() {
        assertEquals(
            RokidKeyMapper.Action.NONE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_VOLUME_UP,
                1,
                1_000L,
            ),
        )
        assertEquals(
            RokidKeyMapper.Action.NONE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_VOLUME_DOWN,
                3,
                1_000L,
            ),
        )
    }

    @Test
    fun repeatedBackPassesThrough() {
        assertEquals(
            RokidKeyMapper.Action.BACK,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_BACK,
                0,
                1_000L,
            ),
        )
        assertEquals(
            RokidKeyMapper.Action.NONE,
            RokidKeyMapper.map(
                RokidKeyMapper.ACTION_DOWN,
                RokidKeyMapper.KEYCODE_BACK,
                1,
                1_000L,
            ),
        )
    }

    @Test
    fun rightThenDownWithinWindowMovesOnce() {
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_DOWN, 1_120L),
        )
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT, 1_241L),
        )
    }

    @Test
    fun leftThenUpWithinWindowMovesOnce() {
        assertEquals(
            RokidKeyMapper.Action.PREVIOUS,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_LEFT, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_UP, 1_120L),
        )
        assertEquals(
            RokidKeyMapper.Action.PREVIOUS,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_ROKID_SWIPE_BACK, 1_241L),
        )
    }

    @Test
    fun rightThenLeftWithinWindowIsStillOneMove() {
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_LEFT, 1_120L),
        )
    }

    @Test
    fun duplicateDoesNotExtendWindow() {
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_DOWN, 1_120L),
        )
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT, 1_241L),
        )
    }

    @Test
    fun selectAndBackAreNotDebounced() {
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_CENTER, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_ENTER, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_ROKID_DOUBLE_TAP, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.BACK,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_BACK, 1_000L),
        )
    }

    @Test
    fun spaceEnterAndCenterMapToSelectWhenNotInEditor() {
        // Editor/dialog passthrough is runtime in RokidControlsController; mapper
        // still emits SELECT so non-editor focus keeps the rail/feed activation path.
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_SPACE, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_ENTER, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_NUMPAD_ENTER, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.SELECT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_CENTER, 1_000L),
        )
    }

    @Test
    fun customSwipeKeysShareDirectionalWindow() {
        assertEquals(
            RokidKeyMapper.Action.NEXT,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_ROKID_SWIPE_FORWARD, 1_000L),
        )
        assertEquals(
            RokidKeyMapper.Action.DUPLICATE,
            RokidKeyMapper.mapKey(RokidKeyMapper.KEYCODE_DPAD_DOWN, 1_120L),
        )
    }

    @Test
    fun directionAndSelectArePlayerControlKeys() {
        assertTrue(RokidKeyMapper.isDirectionOrSelect(RokidKeyMapper.KEYCODE_DPAD_RIGHT))
        assertTrue(RokidKeyMapper.isDirectionOrSelect(RokidKeyMapper.KEYCODE_DPAD_DOWN))
        assertTrue(RokidKeyMapper.isDirectionOrSelect(RokidKeyMapper.KEYCODE_DPAD_CENTER))
        assertFalse(RokidKeyMapper.isDirectionOrSelect(RokidKeyMapper.KEYCODE_BACK))
        assertFalse(RokidKeyMapper.isDirectionOrSelect(RokidKeyMapper.KEYCODE_VOLUME_UP))
        assertTrue(RokidKeyMapper.isRokidKey(RokidKeyMapper.KEYCODE_DPAD_RIGHT))
        assertTrue(RokidKeyMapper.isRokidKey(RokidKeyMapper.KEYCODE_BACK))
        assertFalse(RokidKeyMapper.isRokidKey(RokidKeyMapper.KEYCODE_VOLUME_UP))
        assertFalse(RokidKeyMapper.isRokidKey(42))
        assertFalse(RokidKeyMapper.isRokidKey(53))
    }
}
