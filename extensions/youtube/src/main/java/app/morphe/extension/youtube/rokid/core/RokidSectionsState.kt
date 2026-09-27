/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

enum class RokidSection(val label: String) {
    HOME("Home"),
    SUBSCRIPTIONS("Subscriptions"),
    SEARCH("Search"),
    HISTORY("History"),
    WATCH_LATER("Watch later"),
    YOU("You"),
}

/** One selection drives section rendering and activation. Back ownership stays in the controller. */
class RokidSectionsState {
    var isOpen = false
        private set
    var index = 0
        private set
    var openedFromRoot = false
        private set

    val selected: RokidSection get() = RokidSection.entries[index]

    fun open(fromRoot: Boolean, selected: RokidSection = RokidSection.HOME) {
        isOpen = true
        openedFromRoot = fromRoot
        index = selected.ordinal
    }

    fun move(delta: Int) {
        if (!isOpen) return
        val size = RokidSection.entries.size
        index = ((index + delta) % size + size) % size
    }

    /** Closing a list from the root must also leave the native activity. */
    fun close(): Boolean {
        val exit = isOpen && openedFromRoot
        isOpen = false
        openedFromRoot = false
        return exit
    }

    fun reset() {
        close()
        index = 0
    }

    companion object {
        /** Follow the selected row inside the actual viewport, including short windows. */
        fun scrollOffset(
            index: Int,
            rowStride: Int,
            rowHeight: Int,
            viewport: Int,
            previous: Int,
            lastIndex: Int = RokidSection.entries.lastIndex,
        ): Int {
            if (viewport <= 0 || rowStride <= 0 || rowHeight <= 0 || lastIndex < 0) return 0
            val top = index.coerceIn(0, lastIndex) * rowStride
            val bottom = top + rowHeight
            return when {
                top < previous -> top
                bottom > previous + viewport -> (bottom - viewport).coerceAtLeast(0)
                else -> previous.coerceAtLeast(0)
            }
        }
    }
}
