/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.rokid.RokidControlsController;

/**
 * Named Rokid controls patch gate. {@link #isPatchIncluded()} is rewritten true at patch time.
 */
@SuppressWarnings("unused")
public final class RokidControlsPatch {

    /**
     * Event currently inside {@link #handleKeyEvent}. Same instance through
     * super must not run the controller a second time.
     */
    private static KeyEvent inFlightKeyEvent;

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point from {@code MainActivity#dispatchKeyEvent}.
     * Same KeyEvent instance may re-enter via super; identity-guard that path.
     *
     * @return True if the event was consumed.
     */
    public static boolean handleKeyEvent(Activity activity, KeyEvent event) {
        if (!isPatchIncluded() || activity == null || event == null) {
            return false;
        }
        if (inFlightKeyEvent == event) {
            return false;
        }
        final KeyEvent previous = inFlightKeyEvent;
        inFlightKeyEvent = event;
        try {
            return RokidControlsController.handleKeyEvent(activity, event);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid handleKeyEvent failure", ex);
            return false;
        } finally {
            inFlightKeyEvent = previous;
        }
    }

    /**
     * Injection point from swipe-controls host initialize / onStart.
     */
    public static void attach(Activity activity, ViewGroup contentRoot) {
        if (!isPatchIncluded() || activity == null || contentRoot == null) {
            return;
        }
        try {
            RokidControlsController.attach(activity, contentRoot);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid attach failure", ex);
        }
    }

    public static void reattach(ViewGroup contentRoot) {
        if (!isPatchIncluded() || contentRoot == null) {
            return;
        }
        try {
            RokidControlsController.reattach(contentRoot);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid reattach failure", ex);
        }
    }

    public static void detach() {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            RokidControlsController.detach();
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid detach failure", ex);
        }
    }

    /**
     * Injection point from {@code SwipeControlsHostActivity#onDestroy}.
     */
    public static void onDestroy(Activity activity) {
        if (!isPatchIncluded() || activity == null) {
            return;
        }
        try {
            RokidControlsController.onDestroy(activity);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid onDestroy failure", ex);
        }
    }

    /**
     * Injection point from videoInformationPatch videoTimeHook. Called about once per second.
     */
    public static void onVideoTime(long videoTimeMs) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            RokidControlsController.onVideoTime(videoTimeMs);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid onVideoTime failure", ex);
        }
    }

    /**
     * Injection point from videoInformationPatch onCreateHook.
     * PlaybackController is replaced here; cached time from the previous video is invalid.
     */
    public static void onPlayerInitialized(VideoInformation.PlaybackController ignoredPlayerController) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            RokidControlsController.onPlayerInitialized();
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid onPlayerInitialized failure", ex);
        }
    }

    /**
     * Injection point from videoIdPatch hookVideoId.
     * Identity must change here; time samples are ignored until this matches.
     */
    public static void onVideoId(String videoId) {
        if (!isPatchIncluded()) {
            return;
        }
        try {
            RokidControlsController.onVideoId(videoId);
        } catch (Exception ex) {
            Logger.printException(() -> "Rokid onVideoId failure", ex);
        }
    }

    private RokidControlsPatch() {
    }
}
