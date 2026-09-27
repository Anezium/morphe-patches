/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

/** One entry of YouTube's captions menu. [off] is its "turn off" option. */
data class RokidCaptionChoice(val label: String, val off: Boolean)

/**
 * Captions menu as the glasses panel shows it. Auto-translate is dropped:
 * it opens a second native list of target languages.
 */
object RokidCaptionChoices {
    /** Index of the shown track, or of the "off" option when nothing is shown. */
    fun currentIndex(choices: List<RokidCaptionChoice>, shownIndex: Int): Int {
        if (shownIndex in choices.indices) return shownIndex
        return choices.indexOfFirst { it.off }
    }

    fun hasTracks(choices: List<RokidCaptionChoice>): Boolean = choices.any { !it.off }

    fun isOn(choices: List<RokidCaptionChoice>, current: Int): Boolean {
        return choices.getOrNull(current)?.off == false
    }

    /**
     * On/off switch target: "off" while a track shows; otherwise the track
     * last turned off in this session, else the first track. Null without tracks.
     */
    fun toggleTarget(choices: List<RokidCaptionChoice>, current: Int, lastOnLabel: String?): Int? {
        if (isOn(choices, current)) {
            return choices.indexOfFirst { it.off }.takeIf { it >= 0 }
        }
        val remembered = choices.indexOfFirst { !it.off && it.label == lastOnLabel }
        if (remembered >= 0) return remembered
        return choices.indexOfFirst { !it.off }.takeIf { it >= 0 }
    }

    /** Right-hand value of the language row. */
    fun languageSummary(choices: List<RokidCaptionChoice>, current: Int): String {
        if (!hasTracks(choices)) return "None"
        val choice = choices.getOrNull(current)
        return if (choice == null || choice.off) "Off" else choice.label
    }
}
