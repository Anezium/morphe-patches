/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.youtube.interaction.swipecontrols.swipeControlsPatch
import app.morphe.patches.youtube.layout.player.fullscreen.openVideosFullscreenPatch
import app.morphe.patches.youtube.misc.engagement.engagementPanelHookPatch
import app.morphe.patches.youtube.misc.playertype.playerTypeHookPatch
import app.morphe.patches.youtube.shared.YouTubeMainActivityDispatchKeyEventFingerprint
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.information.videoTimeHook
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.videoid.videoIdPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.setExtensionIsPatchIncluded

private val ROKID_YOUTUBE_COMPATIBILITY = Compatibility(
    name = "YouTube",
    packageName = "com.google.android.youtube",
    apkFileType = ApkFileType.APK_REQUIRED,
    appIconColor = 0xFF0033,
    signatures = setOf(
        "5aad2bee6db95d17e05a08d7d1e64c10a1511879154483916b6ae6c7fd9cb0c6",
        "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
    ),
    targets = listOf(
        AppTarget(
            version = "21.04.223",
            minSdk = 28,
        ),
    ),
)

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/RokidControlsPatch;"

@Suppress("unused")
val rokidControlsPatch = bytecodePatch(
    name = "Rokid controls",
    description = "Adds one-axis Rokid glasses controls: shared directional debounce, " +
        "an outline player rail (play/pause, seek, fullscreen, back), and best-effort feed focus.",
    default = false,
) {
    dependsOn(
        swipeControlsPatch,
        videoInformationPatch,
        videoIdPatch,
        openVideosFullscreenPatch,
        playerTypeHookPatch,
        engagementPanelHookPatch,
    )

    compatibleWith(ROKID_YOUTUBE_COMPATIBILITY)

    execute {
        setExtensionIsPatchIncluded(EXTENSION_CLASS)
        videoTimeHook(EXTENSION_CLASS, "onVideoTime")
        onCreateHook(EXTENSION_CLASS, "onPlayerInitialized")
        hookVideoId("$EXTENSION_CLASS->onVideoId(Ljava/lang/String;)V")

        // MainActivity.dispatchKeyEvent is the Window.Callback entry. It can
        // consume ACTION_DOWN before invoke-super, so the swipe host override
        // never sees key-down. Hook here; leave original code on fallthrough.
        YouTubeMainActivityDispatchKeyEventFingerprint.method.apply {
            val free = findFreeRegister(0)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $EXTENSION_CLASS->handleKeyEvent(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
                    move-result v$free
                    if-eqz v$free, :rokid_unhandled
                    const/4 v$free, 0x1
                    return v$free
                    :rokid_unhandled
                    nop
                """,
            )
        }
    }
}
