/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import java.util.Locale

/**
 * Pure Rokid HUD state. Runtime consumes a key only after the matching action succeeds.
 */
enum class RokidSurface {
    BROWSE,
    SECTIONS,
    PLAYER,

    /** PlayerType WATCH_WHILE_FULLSCREEN: no rail, swipes seek and tap toggles playback. */
    FULLSCREEN,
}

enum class RokidRailItem {
    PLAY_PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    FULLSCREEN,
    BACK,
}

enum class RokidCommand {
    IGNORE,
    CONSUME,
    FEED_PREVIOUS,
    FEED_NEXT,
    FEED_SELECT,
    SECTIONS_OPEN,
    SECTIONS_PREVIOUS,
    SECTIONS_NEXT,
    SECTIONS_SELECT,
    PASS_BACK,
    RAIL_MOVED,

    /** The rail had faded out: the key only brings it back. */
    RAIL_REVEAL,
    ACTIVATE_PLAY_PAUSE,
    ACTIVATE_SEEK_BACK,
    ACTIVATE_SEEK_FORWARD,
    ACTIVATE_FULLSCREEN,
    PLAYER_BACK,
}

data class RokidDispatch(
    val command: RokidCommand,
    val consume: Boolean,
    val railIndex: Int = 0,
    val railItem: RokidRailItem = RokidRailItem.PLAY_PAUSE,
)

/**
 * Time sample bound to one video id. Empty id is not a valid identity.
 */
data class RokidPlaybackCache(
    val videoId: String = "",
    val timeMs: Long = -1L,
)

/**
 * Android-free rail caption helpers. Labels follow VideoState names only when
 * the matching control can actually activate. A successful click is not treated
 * as a playback change.
 */
object RokidRailLabels {
    fun resetPlaybackCache(): RokidPlaybackCache = RokidPlaybackCache()

    /**
     * Bind cache identity on video-id change. Same id keeps the time sample.
     * Empty id drops the cache.
     */
    fun playbackCacheAfterVideoId(
        cache: RokidPlaybackCache,
        videoId: String,
    ): RokidPlaybackCache {
        if (videoId.isEmpty()) {
            return RokidPlaybackCache()
        }
        if (cache.videoId == videoId) {
            return cache
        }
        return RokidPlaybackCache(videoId, -1L)
    }

    /**
     * Stamp time only when [currentVideoId] already matches the bound identity.
     * Unmatched samples do not fall back onto another video's cache.
     */
    fun playbackCacheAfterTimeSample(
        cache: RokidPlaybackCache,
        currentVideoId: String,
        sampleTimeMs: Long,
    ): RokidPlaybackCache {
        if (currentVideoId.isEmpty() || cache.videoId != currentVideoId) {
            return cache
        }
        return RokidPlaybackCache(currentVideoId, sampleTimeMs)
    }

    fun identityBoundCachedTime(cache: RokidPlaybackCache, currentVideoId: String): Long {
        if (currentVideoId.isEmpty() || cache.videoId != currentVideoId) {
            return -1L
        }
        return cache.timeMs
    }

    /**
     * Description engagement panel owns the screen. The player rail must hide
     * and keys must pass through. This is not inferred from a wrapper view.
     */
    fun hidePlayerRailForDescription(descriptionOpen: Boolean): Boolean = descriptionOpen

    fun playPause(canActivate: Boolean, videoStateName: String?): String {
        if (!canActivate) {
            return "Unavailable"
        }
        return when (videoStateName) {
            "PLAYING" -> "Pause"
            "RECOVERABLE_ERROR", "UNRECOVERABLE_ERROR" -> "Error"
            else -> "Play"
        }
    }

    fun seek(backward: Boolean, available: Boolean): String {
        if (!available) {
            return "Unavailable"
        }
        return if (backward) "-10s" else "+10s"
    }

    /**
     * Seek is available only when length and PlaybackController time are known.
     * Cached time cannot restore a missing controller; seekTo still has to
     * return true at runtime.
     */
    fun seekAvailable(videoLengthMs: Long, controllerTimeMs: Long): Boolean {
        return videoLengthMs > 0L && controllerTimeMs >= 0L
    }

    fun resolvePlaybackTime(controllerTimeMs: Long, identityMatchedCachedTimeMs: Long): Long {
        return if (controllerTimeMs >= 0L) controllerTimeMs else identityMatchedCachedTimeMs
    }

    /** "m:ss", or "h:mm:ss" past an hour. Negative means unknown. */
    fun clock(timeMs: Long): String {
        if (timeMs < 0L) {
            return "--:--"
        }
        val totalSeconds = timeMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** "12:04 / 30:23", or the position alone while the length is unknown. */
    fun timeLabel(timeMs: Long, lengthMs: Long): String {
        return if (lengthMs > 0L) "${clock(timeMs)} / ${clock(lengthMs)}" else clock(timeMs)
    }

    /** Played part of the seek bar, 0 while either value is unknown. */
    fun playedFraction(timeMs: Long, lengthMs: Long): Float {
        if (timeMs <= 0L || lengthMs <= 0L) {
            return 0f
        }
        return (timeMs.toFloat() / lengthMs.toFloat()).coerceIn(0f, 1f)
    }

    fun clampSeekTarget(videoTimeMs: Long, offsetMs: Long, videoLengthMs: Long): Long {
        val unclamped = videoTimeMs + offsetMs
        return when {
            unclamped < 0L -> 0L
            unclamped > videoLengthMs -> videoLengthMs
            else -> unclamped
        }
    }
}

class RokidControlsState {
    var railIndex: Int = 0
        private set

    /** Rail faded out after a quiet period; the next rail key reveals it. */
    var railHidden: Boolean = false
        private set

    private var lastSurface: RokidSurface? = null

    fun dispatch(action: RokidKeyMapper.Action, surface: RokidSurface): RokidDispatch {
        syncSurface(surface)

        return when (action) {
            RokidKeyMapper.Action.NONE ->
                RokidDispatch(RokidCommand.IGNORE, consume = false, railIndex = railIndex)
            RokidKeyMapper.Action.DUPLICATE ->
                RokidDispatch(RokidCommand.CONSUME, consume = true, railIndex = railIndex)
            RokidKeyMapper.Action.PREVIOUS -> when (surface) {
                RokidSurface.PLAYER -> revealOr { moveRail(-1) }
                RokidSurface.FULLSCREEN -> direct(RokidRailItem.SEEK_BACK)
                RokidSurface.BROWSE -> feed(RokidCommand.FEED_PREVIOUS)
                RokidSurface.SECTIONS -> RokidDispatch(RokidCommand.SECTIONS_PREVIOUS, true)
            }
            RokidKeyMapper.Action.NEXT -> when (surface) {
                RokidSurface.PLAYER -> revealOr { moveRail(1) }
                RokidSurface.FULLSCREEN -> direct(RokidRailItem.SEEK_FORWARD)
                RokidSurface.BROWSE -> feed(RokidCommand.FEED_NEXT)
                RokidSurface.SECTIONS -> RokidDispatch(RokidCommand.SECTIONS_NEXT, true)
            }
            RokidKeyMapper.Action.SELECT -> when (surface) {
                RokidSurface.PLAYER -> revealOr { activateRail() }
                RokidSurface.FULLSCREEN -> direct(RokidRailItem.PLAY_PAUSE)
                RokidSurface.BROWSE -> feed(RokidCommand.FEED_SELECT)
                RokidSurface.SECTIONS -> RokidDispatch(RokidCommand.SECTIONS_SELECT, true)
            }
            RokidKeyMapper.Action.BACK ->
                // Physical BACK always passes through. Rail Back select is PLAYER_BACK.
                // In fullscreen YouTube leaves fullscreen on its own Back.
                RokidDispatch(RokidCommand.PASS_BACK, consume = false, railIndex = railIndex)
        }
    }

    /**
     * Entering the player from a feed starts on play/pause; coming back from
     * fullscreen keeps the key that entered it. The rail is shown on entry.
     */
    fun syncSurface(surface: RokidSurface) {
        if (surface != lastSurface) {
            if (surface == RokidSurface.PLAYER && lastSurface != RokidSurface.FULLSCREEN) {
                railIndex = 0
            }
            railHidden = false
            lastSurface = surface
        }
    }

    /** Quiet period elapsed. Only the player rail fades. */
    fun hideRail() {
        if (lastSurface == RokidSurface.PLAYER) {
            railHidden = true
        }
    }

    fun reset() {
        railIndex = 0
        railHidden = false
        lastSurface = null
    }

    fun resetForTesting() {
        reset()
    }

    private fun feed(command: RokidCommand) =
        RokidDispatch(command, consume = false, railIndex = railIndex)

    private fun revealOr(onVisible: () -> RokidDispatch): RokidDispatch {
        if (!railHidden) {
            return onVisible()
        }
        railHidden = false
        return RokidDispatch(
            RokidCommand.RAIL_REVEAL,
            consume = true,
            railIndex = railIndex,
            railItem = RokidRailItem.entries[railIndex],
        )
    }

    /** Fullscreen shortcut: the command of [item] without walking the rail. */
    private fun direct(item: RokidRailItem): RokidDispatch {
        return RokidDispatch(commandFor(item), consume = false, railIndex = railIndex, railItem = item)
    }

    private fun moveRail(delta: Int): RokidDispatch {
        val count = RokidRailItem.entries.size
        railIndex = (railIndex + delta + count) % count
        return RokidDispatch(
            RokidCommand.RAIL_MOVED,
            consume = true,
            railIndex = railIndex,
            railItem = RokidRailItem.entries[railIndex],
        )
    }

    private fun activateRail(): RokidDispatch {
        val item = RokidRailItem.entries[railIndex]
        return RokidDispatch(commandFor(item), consume = false, railIndex = railIndex, railItem = item)
    }

    private fun commandFor(item: RokidRailItem): RokidCommand {
        return when (item) {
            RokidRailItem.PLAY_PAUSE -> RokidCommand.ACTIVATE_PLAY_PAUSE
            RokidRailItem.SEEK_BACK -> RokidCommand.ACTIVATE_SEEK_BACK
            RokidRailItem.SEEK_FORWARD -> RokidCommand.ACTIVATE_SEEK_FORWARD
            RokidRailItem.FULLSCREEN -> RokidCommand.ACTIVATE_FULLSCREEN
            RokidRailItem.BACK -> RokidCommand.PLAYER_BACK
        }
    }
}
