/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.rokid

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SearchView
import android.widget.TextView
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.ResourceUtils.getIdentifier
import app.morphe.extension.shared.Utils
import app.morphe.extension.youtube.patches.OpenVideosFullscreenHookPatch
import app.morphe.extension.youtube.patches.RokidControlsPatch
import app.morphe.extension.youtube.patches.VideoInformation
import app.morphe.extension.youtube.shared.EngagementPanel
import app.morphe.extension.youtube.shared.PlayerType
import app.morphe.extension.youtube.shared.VideoState
import java.lang.ref.WeakReference

/**
 * Runtime Rokid controls. Key mapping and rail state are pure; this class talks to YouTube views.
 * Holds only a WeakReference to the host Activity. Recreates the rail if the Activity instance changes.
 */
object RokidControlsController {
    private val state = RokidControlsState()
    private var rail: RokidPlayerRailView? = null
    private var activityRef: WeakReference<Activity> = WeakReference(null)
    private var observersBound = false
    @Volatile
    private var playbackCache: RokidPlaybackCache = RokidRailLabels.resetPlaybackCache()
    private var playerRailArmed: Boolean = false
    private var consumeMatchingKeyUp: Boolean = false

    private val onPlayerTypeChanged: (PlayerType) -> Unit = {
        Utils.runOnMainThreadNowOrLater { refreshRail() }
    }
    private val onVideoStateChanged: (VideoState) -> Unit = {
        Utils.runOnMainThreadNowOrLater { refreshRail() }
    }
    private val onDescriptionChanged: (Boolean) -> Unit = {
        Utils.runOnMainThreadNowOrLater { refreshRail() }
    }

    @JvmStatic
    fun attach(activity: Activity, contentRoot: ViewGroup) {
        if (!RokidControlsPatch.isPatchIncluded()) {
            return
        }
        bindActivity(activity)
        val view = ensureRail(activity)
        addRailTo(contentRoot, view)
        bindObservers()
        refreshRail()
    }

    @JvmStatic
    fun reattach(contentRoot: ViewGroup) {
        val view = rail ?: return
        addRailTo(contentRoot, view)
        refreshRail()
    }

    @JvmStatic
    fun detach() {
        dropRail()
        activityRef = WeakReference(null)
        mutatePlaybackCache { RokidRailLabels.resetPlaybackCache() }
        playerRailArmed = false
        consumeMatchingKeyUp = false
        state.reset()
        RokidKeyMapper.resetDebounce()
        unbindObservers()
    }

    /**
     * Injection point via videoTimeHook. Same 21.04.223 time path as SponsorBlock.
     * Time is stored only after [onVideoId] bound the same identity. A missing
     * PlaybackController still cannot seek from cache alone.
     */
    @JvmStatic
    fun onVideoTime(videoTimeMs: Long) {
        val idAtSample = VideoInformation.getVideoId()
        Utils.runOnMainThreadNowOrLater {
            mutatePlaybackCache { current ->
                RokidRailLabels.playbackCacheAfterTimeSample(current, idAtSample, videoTimeMs)
            }
            refreshRail()
        }
    }

    /**
     * Injection point via onCreateHook. initialize() replaces PlaybackController
     * but does not clear videoId, so previous-video cache must drop here.
     */
    @JvmStatic
    fun onPlayerInitialized() {
        Utils.runOnMainThreadNowOrLater {
            mutatePlaybackCache { RokidRailLabels.resetPlaybackCache() }
            refreshRail()
        }
    }

    /**
     * Injection point via hookVideoId. Identity is bound here; unmatched time
     * samples are ignored until this matches.
     */
    @JvmStatic
    fun onVideoId(videoId: String?) {
        val id = videoId ?: ""
        Utils.runOnMainThreadNowOrLater {
            mutatePlaybackCache { current ->
                RokidRailLabels.playbackCacheAfterVideoId(current, id)
            }
            refreshRail()
        }
    }

    @JvmStatic
    fun onDestroy(activity: Activity) {
        val bound = activityRef.get()
        if (bound == null || bound === activity) {
            detach()
        }
    }

    @JvmStatic
    fun handleKeyEvent(activity: Activity, event: KeyEvent): Boolean {
        if (!RokidControlsPatch.isPatchIncluded()) {
            return false
        }
        Utils.verifyOnMainThread()
        val surface = currentSurface()
        val bypass = classifyBypass(activity)
        if (bypass != RokidKeyBypass.Reason.NONE) {
            consumeMatchingKeyUp = false
            logKey(event, bypass, surface, state.railIndex, "NONE", false)
            return false
        }
        if (
            event.action == KeyEvent.ACTION_UP &&
            consumeMatchingKeyUp &&
            RokidKeyMapper.isDirectionOrSelect(event.keyCode)
        ) {
            consumeMatchingKeyUp = false
            logKey(event, RokidKeyBypass.Reason.NONE, surface, state.railIndex, "CONSUME_UP", true)
            return true
        }
        val action = RokidKeyMapper.map(
            event.action,
            event.keyCode,
            event.repeatCount,
            SystemClock.elapsedRealtime(),
        )
        val dispatch = state.dispatch(action, surface)
        val consumed = execute(activity, dispatch)
        consumeMatchingKeyUp =
            event.action == KeyEvent.ACTION_DOWN &&
                consumed &&
                RokidKeyMapper.isDirectionOrSelect(event.keyCode)
        refreshRail()
        logKey(
            event,
            RokidKeyBypass.Reason.NONE,
            surface,
            dispatch.railIndex,
            dispatch.command.name,
            consumed,
        )
        return consumed
    }

    private fun bindActivity(activity: Activity) {
        val previous = activityRef.get()
        if (previous !== activity) {
            dropRail()
            activityRef = WeakReference(activity)
        }
    }

    private fun ensureRail(activity: Activity): RokidPlayerRailView {
        val existing = rail
        if (existing != null) {
            return existing
        }
        val created = RokidPlayerRailView(activity)
        created.layoutParams = railLayoutParams(activity)
        rail = created
        return created
    }

    private fun addRailTo(contentRoot: ViewGroup, view: RokidPlayerRailView) {
        val params = view.layoutParams ?: railLayoutParams(contentRoot.context)
        (view.parent as? ViewGroup)?.removeView(view)
        contentRoot.addView(view, params)
    }

    private fun dropRail() {
        val view = rail
        rail = null
        (view?.parent as? ViewGroup)?.removeView(view)
    }

    private fun railLayoutParams(context: Context): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
        )
        params.bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
        return params
    }

    private fun bindObservers() {
        if (observersBound) {
            return
        }
        PlayerType.onChange += onPlayerTypeChanged
        VideoState.onChange += onVideoStateChanged
        EngagementPanel.onDescriptionChange += onDescriptionChanged
        observersBound = true
    }

    private fun unbindObservers() {
        if (!observersBound) {
            return
        }
        PlayerType.onChange -= onPlayerTypeChanged
        VideoState.onChange -= onVideoStateChanged
        EngagementPanel.onDescriptionChange -= onDescriptionChanged
        observersBound = false
    }

    private fun currentSurface(): RokidSurface {
        return if (PlayerType.current.isMaximizedOrFullscreen()) {
            RokidSurface.PLAYER
        } else {
            RokidSurface.BROWSE
        }
    }

    private fun refreshRail() {
        val view = rail ?: return
        val surface = currentSurface()
        state.syncSurface(surface)
        if (surface != RokidSurface.PLAYER) {
            playerRailArmed = false
            view.hideRail()
            return
        }
        if (RokidRailLabels.hidePlayerRailForDescription(EngagementPanel.isDescription())) {
            view.hideRail()
            return
        }
        if (!playerRailArmed) {
            playerRailArmed = true
            // Length/time often arrive after PlayerType. One delayed paint, no key required.
            Utils.runOnMainThreadDelayed({ refreshRail() }, 800)
        }
        val activity = activityRef.get()
        val playCanActivate = activity != null && RokidPlayPauseController.canActivate(activity)
        val videoState = VideoState.current
        val length = VideoInformation.getVideoLength()
        val controllerTime = VideoInformation.getVideoTime()
        val currentVideoId = VideoInformation.getVideoId()
        mutatePlaybackCache { current ->
            if (current.videoId.isNotEmpty() && current.videoId != currentVideoId) {
                RokidRailLabels.resetPlaybackCache()
            } else {
                current
            }
        }
        val seekAvailable = RokidRailLabels.seekAvailable(length, controllerTime)
        if (!playCanActivate || !seekAvailable) {
            Logger.printDebug {
                "Rokid rail: type=${PlayerType.current} state=$videoState " +
                    "len=$length controllerTime=$controllerTime " +
                    "videoId=$currentVideoId cached=$playbackCache " +
                    "playCanActivate=$playCanActivate seekAvailable=$seekAvailable"
            }
        }
        view.showAt(
            index = state.railIndex,
            playPauseLabel = RokidRailLabels.playPause(playCanActivate, videoState?.name),
            playPauseAvailable = playCanActivate,
            seekBackLabel = RokidRailLabels.seek(backward = true, available = seekAvailable),
            seekForwardLabel = RokidRailLabels.seek(backward = false, available = seekAvailable),
            seekAvailable = seekAvailable,
        )
    }

    /**
     * SPACE/ENTER/DPAD_CENTER reach a real IME field or a separate dialog window.
     * Overlay TextViews (titles, selectable labels, rail captions) are display
     * text: they must not pass DPAD through to YouTube's native seek handler.
     */
    private fun classifyBypass(activity: Activity): RokidKeyBypass.Reason {
        val descriptionOpen = RokidRailLabels.hidePlayerRailForDescription(
            EngagementPanel.isDescription(),
        )
        val focus = activity.currentFocus
        val focusShown = focus != null && focus.isShown
        val editableField = focusShown && isEditableField(focus)
        val decor = activity.window?.decorView
        val foreignWindow = focusShown && focus != null && decor != null && focus.rootView !== decor
        return RokidKeyBypass.classify(descriptionOpen, editableField, foreignWindow)
    }

    private fun isEditableField(focus: View?): Boolean {
        if (focus == null) {
            return false
        }
        if (focus is EditText || focus is SearchView) {
            return true
        }
        if (focus is TextView) {
            return RokidKeyBypass.isEditableInputType(focus.inputType)
        }
        return false
    }

    /**
     * Always-on, identity-free key trace for this opt-in patch.
     * Rokid navigation/control keys only. Editor keystrokes are never logged.
     * Grep: logcat -s RokidControls
     */
    private fun logKey(
        event: KeyEvent,
        bypass: RokidKeyBypass.Reason,
        surface: RokidSurface,
        index: Int,
        command: String,
        consume: Boolean,
    ) {
        if (bypass == RokidKeyBypass.Reason.EDITOR) {
            return
        }
        if (!RokidKeyMapper.isRokidKey(event.keyCode)) {
            return
        }
        Log.i(
            "RokidControls",
            "key action=${event.action} code=${event.keyCode} repeat=${event.repeatCount} " +
                "bypass=${bypass.name} surface=$surface index=$index " +
                "command=$command consume=$consume",
        )
    }

    private fun execute(activity: Activity, dispatch: RokidDispatch): Boolean {
        return when (dispatch.command) {
            RokidCommand.IGNORE -> false
            RokidCommand.CONSUME, RokidCommand.RAIL_MOVED -> true
            RokidCommand.PASS_BACK -> false
            RokidCommand.FEED_PREVIOUS -> stepFeed(activity, next = false)
            RokidCommand.FEED_NEXT -> stepFeed(activity, next = true)
            RokidCommand.FEED_SELECT -> activateFocused(activity)
            RokidCommand.ACTIVATE_PLAY_PAUSE -> {
                val clicked = RokidPlayPauseController.tryPerformClick(activity)
                if (!clicked) {
                    Logger.printDebug { "Rokid play/pause: activation failed, leaving rail unchanged" }
                }
                clicked
            }
            RokidCommand.ACTIVATE_SEEK_BACK -> seekRelative(-10_000L)
            RokidCommand.ACTIVATE_SEEK_FORWARD -> seekRelative(10_000L)
            RokidCommand.ACTIVATE_FULLSCREEN -> toggleFullscreen()
            RokidCommand.PLAYER_BACK -> dispatchActualBack(activity)
        }
    }

    private fun seekRelative(offsetMs: Long): Boolean {
        Utils.verifyOnMainThread()
        val length = VideoInformation.getVideoLength()
        val controllerTime = VideoInformation.getVideoTime()
        if (!RokidRailLabels.seekAvailable(length, controllerTime)) {
            Logger.printDebug {
                "Rokid seek: controller unavailable (length=$length controllerTime=$controllerTime)"
            }
            return false
        }
        val target = RokidRailLabels.clampSeekTarget(controllerTime, offsetMs, length)
        val issued = VideoInformation.seekTo(target)
        if (!issued) {
            Logger.printDebug { "Rokid seekTo($target) returned false" }
        }
        return issued
    }

    /**
     * Cache is mutated only on the main thread. videoTime/init/id hooks hop here
     * before touching [playbackCache].
     */
    private fun mutatePlaybackCache(update: (RokidPlaybackCache) -> RokidPlaybackCache) {
        Utils.verifyOnMainThread()
        playbackCache = update(playbackCache)
    }

    private fun toggleFullscreen(): Boolean {
        Utils.verifyOnMainThread()
        return if (PlayerType.current == PlayerType.WATCH_WHILE_FULLSCREEN) {
            OpenVideosFullscreenHookPatch.exitFullscreenMode()
        } else {
            OpenVideosFullscreenHookPatch.enterFullscreenMode()
        }
    }

    @Suppress("DEPRECATION")
    private fun dispatchActualBack(activity: Activity): Boolean {
        Utils.verifyOnMainThread()
        return try {
            activity.onBackPressed()
            Logger.printDebug { "Rokid rail Back: onBackPressed dispatched" }
            true
        } catch (ex: Exception) {
            Logger.printException({ "Rokid rail Back failed" }, ex)
            false
        }
    }

    private fun activateFocused(activity: Activity): Boolean {
        val container = findResultsContainer(activity)
        if (container != null) {
            val item = feedItemRoot(activity.currentFocus, container)
                ?: attachedFeedItems(container).firstOrNull()
            if (item == null) {
                Logger.printDebug { "Rokid feed select: no results item" }
                return false
            }
            return activateFeedItem(item)
        }
        val focused = activity.currentFocus
        if (focused == null || !focused.isShown || !focused.isAttachedToWindow) {
            Logger.printDebug { "Rokid feed select: no attached focus" }
            return false
        }
        if (isChromeView(focused) || !focused.isClickable) {
            Logger.printDebug { "Rokid feed select: focus is not an activatable feed item" }
            return false
        }
        val clicked = focused.performClick()
        if (!clicked) {
            Logger.printDebug { "Rokid feed select: click returned false" }
        }
        return clicked
    }

    /**
     * Search results RecyclerView [results] is focused and scrollable. Horizontal
     * focusSearch from it lands on [pivot_bar] Shorts. Stay inside the container
     * and step attached item children vertically.
     */
    private fun stepFeed(activity: Activity, next: Boolean): Boolean {
        val container = findResultsContainer(activity) ?: return false
        val items = attachedFeedItems(container)
        val fromItem = feedItemRoot(activity.currentFocus, container)
        if (items.isNotEmpty()) {
            val currentIndex = fromItem?.let { items.indexOf(it) } ?: -1
            val targetItem = when {
                currentIndex < 0 -> if (next) items.first() else items.last()
                next -> items.getOrNull(currentIndex + 1)
                else -> items.getOrNull(currentIndex - 1)
            }
            if (targetItem != null) {
                return focusFeedItem(targetItem)
            }
        }
        val from = fromItem ?: items.lastOrNull() ?: container
        return focusSearchContained(from, container, next)
    }

    private fun findResultsContainer(activity: Activity): ViewGroup? {
        shownGroup(activity, RokidFeedScope.RESULTS)?.let { return it }
        shownGroup(activity, RokidFeedScope.LOADING_LAYOUT)?.let { loading ->
            for (i in 0 until loading.childCount) {
                val child = loading.getChildAt(i) as? ViewGroup ?: continue
                if (child.isShown && child.isAttachedToWindow) {
                    return child
                }
            }
            return loading
        }
        return shownGroup(activity, RokidFeedScope.PANE_FRAGMENT_CONTENTS)
    }

    private fun shownGroup(activity: Activity, name: String): ViewGroup? {
        val id = resolveViewId(activity, name)
        if (id == 0) {
            return null
        }
        val view = activity.findViewById<ViewGroup>(id)
        return if (view != null && view.isShown && view.isAttachedToWindow) {
            view
        } else {
            null
        }
    }

    private fun attachedFeedItems(container: ViewGroup): List<View> {
        val items = ArrayList<View>(container.childCount)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) ?: continue
            if (child.isShown && child.isAttachedToWindow && !isChromeView(child)) {
                items.add(child)
            }
        }
        items.sortBy { it.top }
        return items
    }

    private fun feedItemRoot(view: View?, container: ViewGroup): View? {
        if (view == null || view === container || !isInside(view, container) || isChromeView(view)) {
            return null
        }
        var current = view
        var parent = current.parent as? View
        while (parent != null && parent !== container) {
            current = parent
            parent = current.parent as? View
        }
        return if (parent === container) current else null
    }

    private fun focusSearchContained(from: View, container: ViewGroup, next: Boolean): Boolean {
        val directions = if (next) {
            intArrayOf(View.FOCUS_DOWN, View.FOCUS_FORWARD)
        } else {
            intArrayOf(View.FOCUS_UP, View.FOCUS_BACKWARD)
        }
        val fromItem = feedItemRoot(from, container)
        for (direction in directions) {
            val found = from.focusSearch(direction) ?: continue
            if (found === from || isChromeView(found) || !isInside(found, container)) {
                continue
            }
            val item = feedItemRoot(found, container) ?: continue
            if (item === fromItem) {
                continue
            }
            if (focusFeedItem(item)) {
                return true
            }
        }
        return false
    }

    private fun focusFeedItem(item: View): Boolean {
        val target = preferredActivation(item)
        if (target.requestFocus()) {
            target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height), false)
            return true
        }
        if (item !== target && item.requestFocus()) {
            item.requestRectangleOnScreen(android.graphics.Rect(0, 0, item.width, item.height), false)
            return true
        }
        Logger.printDebug { "Rokid feed step: item did not take focus" }
        return false
    }

    private fun activateFeedItem(item: View): Boolean {
        val target = preferredActivation(item)
        if (!target.isClickable) {
            Logger.printDebug { "Rokid feed select: item has no clickable target" }
            return false
        }
        val clicked = target.performClick()
        if (!clicked) {
            Logger.printDebug { "Rokid feed select: click returned false" }
        }
        return clicked
    }

    private fun preferredActivation(item: View): View {
        val clickables = ArrayList<View>()
        collectClickable(item, clickables)
        val itemArea = item.width * item.height
        val large = clickables.filter { clickable ->
            RokidFeedScope.preferLargeClickable(itemArea, clickable.width * clickable.height)
        }
        // Dumpsys: item roots are focusable ViewGroups; the card is a large Litho
        // ComponentHost. Avatar/overflow ImageViews are clickable but tiny.
        // Never fall back to those chips.
        return large.maxByOrNull { it.width * it.height } ?: item
    }

    private fun collectClickable(view: View, out: MutableList<View>) {
        if (view.isShown && view.isClickable && view.isAttachedToWindow) {
            out.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectClickable(view.getChildAt(i), out)
            }
        }
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun isChromeView(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (chromeEntryName(current)?.let { RokidFeedScope.isChrome(it) } == true) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun chromeEntryName(view: View): String? {
        return try {
            if (view.id == View.NO_ID) {
                null
            } else {
                view.resources.getResourceEntryName(view.id)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveViewId(activity: Activity, name: String): Int {
        val fromContext = ResourceUtils.getIdentifier(ResourceType.ID, name)
        if (fromContext != 0) {
            return fromContext
        }
        return getIdentifier(activity, ResourceType.ID, name)
    }
}
