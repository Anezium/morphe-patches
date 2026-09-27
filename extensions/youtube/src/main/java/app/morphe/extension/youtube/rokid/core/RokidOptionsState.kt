/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import java.util.Locale

enum class RokidOptionsPage {
    MAIN,
    SPEED,
    LANGUAGE,
}

enum class RokidOption {
    SPEED,
    CAPTIONS,
    LANGUAGE,
}

/** One drawn row: [value] sits right-aligned, [current] marks the active choice of a list. */
data class RokidOptionRow(
    val label: String,
    val value: String? = null,
    val current: Boolean = false,
    val enabled: Boolean = true,
)

object RokidPlaybackSpeeds {
    val values = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    fun label(speed: Float): String {
        val text = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.')
        return "${text}×"
    }

    /** Row of the listed speed closest to [speed], so a custom native speed still lands somewhere. */
    fun nearestIndex(speed: Float): Int {
        return values.indices.minByOrNull { kotlin.math.abs(values[it] - speed) } ?: values.indexOf(1f)
    }
}

/**
 * Player options panel opened from the rail: a main list, and one sub-list
 * per option that has choices. Back and the pages are owned here; the
 * controller applies the chosen value.
 */
class RokidOptionsState {
    var isOpen = false
        private set
    var page = RokidOptionsPage.MAIN
        private set
    var index = 0
        private set

    val selectedOption: RokidOption get() = RokidOption.entries[index.coerceIn(0, RokidOption.entries.lastIndex)]

    fun open() {
        isOpen = true
        page = RokidOptionsPage.MAIN
        index = 0
    }

    /** Opens a sub-list with [initialIndex] focused, usually the current value. */
    fun enter(page: RokidOptionsPage, initialIndex: Int) {
        if (!isOpen) return
        this.page = page
        index = initialIndex.coerceAtLeast(0)
    }

    fun move(delta: Int, rowCount: Int) {
        if (!isOpen || rowCount <= 0) return
        index = ((index + delta) % rowCount + rowCount) % rowCount
    }

    /** Back from a sub-list returns to its main row; from the main list it closes the panel. */
    fun back() {
        if (!isOpen) return
        if (page == RokidOptionsPage.MAIN) {
            close()
            return
        }
        index = when (page) {
            RokidOptionsPage.SPEED -> RokidOption.SPEED.ordinal
            else -> RokidOption.LANGUAGE.ordinal
        }
        page = RokidOptionsPage.MAIN
    }

    fun close() {
        isOpen = false
        page = RokidOptionsPage.MAIN
        index = 0
    }
}
