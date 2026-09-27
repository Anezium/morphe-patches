/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/**
 * Names from the verified 21.04 search results tree:
 * [search_confirmed.xml] accessibility dump and [activity_views.txt] dumpsys.
 *
 * Immediate children of [results] are the feed items (obfuscated ViewGroups).
 * The large clickable descendant is a Litho ComponentHost, not an XML Button.
 * Navbar, toolbar, and miniplayer sit outside [results] and must be excluded.
 */
object RokidFeedScope {
    const val RESULTS = "results"
    const val LOADING_LAYOUT = "loading_layout"
    const val PANE_FRAGMENT_CONTENTS = "pane_fragment_contents"

    val containerNames = listOf(RESULTS, LOADING_LAYOUT, PANE_FRAGMENT_CONTENTS)

    /**
     * Chrome set GONE in glasses mode, from the 21.04 home tree (dumpsys):
     * top bar, tab bar and the filter chips bar. The tab bar's avatar keeps
     * its own id and is hidden with it.
     */
    val hiddenChromeNames = listOf(
        "appbar_layout",
        "toolbar_container",
        "toolbar",
        "filter_bar",
        "bottom_bar_container",
        "pivot_bar",
        "pivot_bar_thumbnail",
    )

    fun isResultsContainer(entryName: String): Boolean {
        return entryName == RESULTS ||
            entryName == LOADING_LAYOUT ||
            entryName == PANE_FRAGMENT_CONTENTS
    }

    fun isChrome(entryName: String): Boolean {
        if (entryName.contains("floaty", ignoreCase = true)) {
            return true
        }
        if (entryName.contains("miniplayer", ignoreCase = true)) {
            return true
        }
        return when (entryName) {
            "pivot_bar",
            "pivot_bar_thumbnail",
            "bottom_bar_container",
            "toolbar",
            "toolbar_container",
            "search_box",
            "search_query",
            "search_clear",
            "voice_search",
            "appbar_layout",
            -> true
            else -> false
        }
    }

    /**
     * Prefer a large clickable descendant over channel/menu chips.
     * Tiny clickables relative to the item are not the sample card.
     */
    fun preferLargeClickable(itemArea: Int, clickableArea: Int): Boolean {
        if (itemArea <= 0 || clickableArea <= 0) {
            return false
        }
        return clickableArea * 4 >= itemArea
    }
}
