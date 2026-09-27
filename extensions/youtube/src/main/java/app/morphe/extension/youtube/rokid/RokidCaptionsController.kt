/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.RokidControlsPatch.NativeCaptionTrack
import app.morphe.extension.youtube.patches.RokidControlsPatch.NativeCaptions
import java.lang.ref.WeakReference

/**
 * Captions through YouTube's own subtitles controller: selecting a track here
 * is the native menu's preferred-track choice, so it sticks to later videos.
 */
object RokidCaptionsController {
    private var captions = WeakReference<NativeCaptions>(null)
    private var lastOnLabel: String? = null

    class Snapshot(
        val tracks: List<Any>,
        val choices: List<RokidCaptionChoice>,
        val current: Int,
    )

    @JvmStatic
    fun bind(controller: NativeCaptions) {
        captions = WeakReference(controller)
    }

    /** Null while no video with captions data is loaded. */
    fun snapshot(): Snapshot? {
        Utils.verifyOnMainThread()
        val controller = captions.get() ?: return null
        return try {
            val tracks = controller.patch_getCaptionTracks()
                ?.filterNotNull()
                ?.filter { (it as? NativeCaptionTrack)?.patch_isAutoTranslate() != true }
                .orEmpty()
            if (tracks.isEmpty()) return null
            val choices = tracks.map {
                RokidCaptionChoice(it.toString(), (it as? NativeCaptionTrack)?.patch_isCaptionsOff() == true)
            }
            val shown = controller.patch_getCaptionTrack()
            val current = RokidCaptionChoices.currentIndex(choices, tracks.indexOf(shown))
            Snapshot(tracks, choices, current)
        } catch (ex: Exception) {
            Logger.printException({ "Rokid captions snapshot failed" }, ex)
            null
        }
    }

    fun select(snapshot: Snapshot, index: Int): Boolean {
        Utils.verifyOnMainThread()
        val controller = captions.get() ?: return false
        val track = snapshot.tracks.getOrNull(index) ?: return false
        val current = snapshot.choices.getOrNull(snapshot.current)
        if (current != null && !current.off) lastOnLabel = current.label
        return try {
            controller.patch_setCaptionTrack(track)
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
