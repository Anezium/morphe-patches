/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * Rectangle in window pixels. Android-free so feed geometry is testable.
 */
data class RokidBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/**
 * Feed geometry for the glasses mode: where the focus ring goes, how far the
 * results list scrolls so the focused card sits at the top, and which adapter
 * position the counter shows.
 */
object RokidFeedGeometry {
    /** Exclude avatars and Shorts while accepting both grid and list thumbnails. */
    fun isVideoThumbnail(width: Int, height: Int): Boolean =
        width >= 80 && height >= 45 &&
            kotlin.math.abs(width * 9L - height * 16L) * 100 <= width * 9L * 8

    /**
     * Ring [outset] pixels outside the target, pulled back inside [bounds].
     * Feed cards are full width: without the clamp the side strokes fall off
     * the 480 px canvas and the ring reads as two lines.
     */
    fun ringBox(target: RokidBox, outset: Int, stroke: Int, bounds: RokidBox): RokidBox {
        val half = (stroke + 1) / 2
        return RokidBox(
            maxOf(target.left - outset, bounds.left + half),
            maxOf(target.top - outset, bounds.top + half),
            minOf(target.right + outset, bounds.right - half),
            minOf(target.bottom + outset, bounds.bottom - half),
        )
    }

    /**
     * Scroll delta that puts the card top on the first row the HUD header does
     * not cover, leaving room for the ring above the card.
     */
    fun snapDelta(
        itemTopInWindow: Int,
        containerContentTopInWindow: Int,
        headerBottomInWindow: Int,
        ringOutset: Int,
    ): Int {
        val target = maxOf(containerContentTopInWindow, headerBottomInWindow + ringOutset)
        return itemTopInWindow - target
    }

    /**
     * Litho thumbnails have no id. A descendant at the top of the card that
     * spans its width with a 16:9 box is the thumbnail the ring wraps.
     */
    fun looksLikeThumbnail(width: Int, height: Int, itemWidth: Int, topInItem: Int): Boolean {
        if (width <= 0 || height <= 0 || itemWidth <= 0) {
            return false
        }
        if (width * 10 < itemWidth * 9) {
            return false
        }
        if (topInItem < 0 || topInItem * 8 > height) {
            return false
        }
        val deviation = kotlin.math.abs(width * 9 - height * 16)
        return deviation * 100 <= width * 9 * 8
    }

    /**
     * Zero-based adapter position of [focusedIndex] among the laid-out children
     * (ordered top to bottom), given the adapter position of the first child
     * that intersects the viewport. -1 when unknown.
     */
    fun adapterPosition(
        firstVisiblePosition: Int,
        tops: IntArray,
        bottoms: IntArray,
        viewportTop: Int,
        viewportBottom: Int,
        focusedIndex: Int,
    ): Int {
        if (firstVisiblePosition < 0 || focusedIndex !in tops.indices || tops.size != bottoms.size) {
            return -1
        }
        val firstVisible = tops.indices.firstOrNull { i ->
            bottoms[i] > viewportTop && tops[i] < viewportBottom
        } ?: return -1
        val position = firstVisiblePosition + (focusedIndex - firstVisible)
        return if (position >= 0) position else -1
    }
}
