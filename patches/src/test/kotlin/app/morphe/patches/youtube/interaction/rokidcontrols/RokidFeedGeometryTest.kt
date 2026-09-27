/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.extension.youtube.rokid.RokidBox
import app.morphe.extension.youtube.rokid.RokidFeedGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RokidFeedGeometryTest {
    private val screen = RokidBox(0, 0, 480, 640)

    @Test
    fun ringSitsOutsideACardThatFitsTheScreen() {
        val ring = RokidFeedGeometry.ringBox(RokidBox(24, 100, 456, 343), outset = 4, stroke = 4, bounds = screen)
        assertEquals(RokidBox(20, 96, 460, 347), ring)
    }

    @Test
    fun ringOfAFullWidthCardIsPulledBackOnScreen() {
        val ring = RokidFeedGeometry.ringBox(RokidBox(0, 62, 480, 332), outset = 4, stroke = 4, bounds = screen)
        assertEquals(2, ring.left)
        assertEquals(478, ring.right)
        assertEquals(58, ring.top)
        assertEquals(336, ring.bottom)
    }

    @Test
    fun ringNeverRisesAboveTheResultsTop() {
        val bounds = RokidBox(0, 72, 480, 640)
        val ring = RokidFeedGeometry.ringBox(RokidBox(0, 72, 480, 342), outset = 4, stroke = 4, bounds = bounds)
        assertEquals(74, ring.top)
    }

    @Test
    fun snapPutsTheCardUnderTheHeader() {
        // Results start at the window top once the app bar is gone.
        assertEquals(537, RokidFeedGeometry.snapDelta(600, 0, 54, 9))
        assertEquals(-63, RokidFeedGeometry.snapDelta(0, 0, 54, 9))
    }

    @Test
    fun snapUsesTheContainerTopWhenItIsBelowTheHeader() {
        assertEquals(402, RokidFeedGeometry.snapDelta(474, 72, 54, 9))
        assertEquals(0, RokidFeedGeometry.snapDelta(72, 72, 54, 9))
    }

    @Test
    fun thumbnailIsAFullWidthSixteenByNineBoxAtTheTop() {
        assertTrue(RokidFeedGeometry.looksLikeThumbnail(480, 270, 480, 0))
        assertTrue(RokidFeedGeometry.looksLikeThumbnail(456, 256, 480, 12))
        assertFalse(RokidFeedGeometry.looksLikeThumbnail(480, 402, 480, 0))
        assertFalse(RokidFeedGeometry.looksLikeThumbnail(160, 90, 480, 0))
        assertFalse(RokidFeedGeometry.looksLikeThumbnail(480, 270, 480, 132))
        assertFalse(RokidFeedGeometry.looksLikeThumbnail(0, 0, 480, 0))
    }

    @Test
    fun adapterPositionCountsFromTheFirstVisibleChild() {
        val tops = intArrayOf(-390, 60, 510)
        val bottoms = intArrayOf(48, 498, 948)
        // Child 0 still shows 48 px at the top, so it is the first visible one.
        assertEquals(5, RokidFeedGeometry.adapterPosition(4, tops, bottoms, 0, 568, 1))
        assertEquals(6, RokidFeedGeometry.adapterPosition(4, tops, bottoms, 0, 568, 2))
    }

    @Test
    fun adapterPositionSkipsChildrenAboveTheViewport() {
        val tops = intArrayOf(-500, -40, 410)
        val bottoms = intArrayOf(-62, 398, 848)
        assertEquals(7, RokidFeedGeometry.adapterPosition(7, tops, bottoms, 0, 568, 1))
        assertEquals(6, RokidFeedGeometry.adapterPosition(7, tops, bottoms, 0, 568, 0))
    }

    @Test
    fun adapterPositionIsUnknownWithoutAnAnchor() {
        val tops = intArrayOf(0, 450)
        val bottoms = intArrayOf(438, 888)
        assertEquals(-1, RokidFeedGeometry.adapterPosition(-1, tops, bottoms, 0, 568, 1))
        assertEquals(-1, RokidFeedGeometry.adapterPosition(0, tops, bottoms, 0, 568, -1))
        assertEquals(-1, RokidFeedGeometry.adapterPosition(0, tops, intArrayOf(438), 0, 568, 1))
    }
}
