/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import app.morphe.extension.shared.Logger
import android.os.SystemClock
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.RokidControlsPatch.NativeCaptionTrack
import app.morphe.extension.youtube.patches.RokidControlsPatch.NativeCaptions
import java.util.Collections
import java.util.WeakHashMap

/**
 * Captions through YouTube's own subtitles controller: selecting a track here
 * is the native menu's preferred-track choice, so it sticks to later videos.
 */
object RokidCaptionsController {
    /** YouTube builds one controller per player instance; only the active one has tracks. */
    private val controllers: MutableSet<NativeCaptions> = Collections.newSetFromMap(WeakHashMap())
    private var lastOnLabel: String? = null

    /**
     * YouTube updates its shown track some time after a selection, so a quick
     * second press would read the old track. The pending choice stands in for
     * it until YouTube agrees or [PENDING_MS] pass.
     */
    private var pendingLabel: String? = null
    private var pendingUntil = 0L
    private const val PENDING_MS = 4_000L

    class Snapshot(
        val controller: NativeCaptions,
        val tracks: List<Any>,
        val choices: List<RokidCaptionChoice>,
        val current: Int,
    )

    @JvmStatic
    fun bind(controller: NativeCaptions) {
        controllers.add(controller)
    }

    /** Null while no video with captions data is loaded. */
    fun snapshot(): Snapshot? {
        Utils.verifyOnMainThread()
        // A controller showing a track wins; else the first one with a track list.
        val candidates = controllers.toList()
        return candidates.firstNotNullOfOrNull { controller ->
            snapshotOf(controller)?.takeIf { RokidCaptionChoices.isOn(it.choices, it.current) }
        } ?: candidates.firstNotNullOfOrNull { snapshotOf(it) }
    }

    private fun snapshotOf(controller: NativeCaptions): Snapshot? {
        return try {
            val tracks = controller.patch_getCaptionTracks()
                ?.filterNotNull()
                ?.filter { (it as? NativeCaptionTrack)?.patch_isAutoTranslate() != true }
                .orEmpty()
            if (tracks.isEmpty()) return null
            val choices = tracks.map {
                RokidCaptionChoice(it.toString(), (it as? NativeCaptionTrack)?.patch_isCaptionsOff() == true)
            }
            val shown = tracks.indexOf(controller.patch_getCaptionTrack())
            val pending = pendingLabel?.takeIf { SystemClock.uptimeMillis() < pendingUntil }
            val pendingIndex = choices.indexOfFirst { it.label == pending }
            if (pendingIndex < 0 || pendingIndex == shown) pendingLabel = null
            val current = RokidCaptionChoices.currentIndex(choices, if (pendingIndex >= 0) pendingIndex else shown)
            Snapshot(controller, tracks, choices, current)
        } catch (ex: Exception) {
            Logger.printException({ "Rokid captions snapshot failed" }, ex)
            null
        }
    }

    fun select(snapshot: Snapshot, index: Int): Boolean {
        Utils.verifyOnMainThread()
        val controller = snapshot.controller
        val track = snapshot.tracks.getOrNull(index) ?: return false
        val current = snapshot.choices.getOrNull(snapshot.current)
        if (current != null && !current.off) lastOnLabel = current.label
        return try {
            controller.patch_setCaptionTrack(track)
            pendingLabel = snapshot.choices[index].label
            pendingUntil = SystemClock.uptimeMillis() + PENDING_MS
            true
        } catch (ex: Exception) {
            Logger.printException({ "Rokid captions select failed" }, ex)
            false
        }
    }

    fun toggle(snapshot: Snapshot): Boolean {
        val target = RokidCaptionChoices.toggleTarget(snapshot.choices, snapshot.current, lastOnLabel)
            ?: return false
        return select(snapshot, target)
    }
}
