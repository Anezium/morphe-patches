/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.patches.youtube.layout.sponsorblock.ControlsOverlayFingerprint
import app.morphe.patches.youtube.misc.playercontrols.PlayerTopControlsInflateFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import app.morphe.patches.youtube.interaction.swipecontrols.swipeControlsPatch
import app.morphe.patches.youtube.layout.player.fullscreen.openVideosFullscreenPatch
import app.morphe.patches.youtube.layout.player.fullscreen.AdPlayerFullscreenFingerprint
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

private const val CONTROLS_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/RokidControlsPatch$NativeControls;"

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
        resourceMappingPatch,
    )

    compatibleWith(ROKID_YOUTUBE_COMPATIBILITY)

    execute {
        setExtensionIsPatchIncluded(EXTENSION_CLASS)
        videoTimeHook(EXTENSION_CLASS, "onVideoTime")
        onCreateHook(EXTENSION_CLASS, "onPlayerInitialized")
        hookVideoId("$EXTENSION_CLASS->onVideoId(Ljava/lang/String;)V")

        // The native fullscreen button requests landscape first. Its existing
        // same-orientation branch enters fullscreen without rotating the HUD.
        val fullscreenClass = AdPlayerFullscreenFingerprint.instructionMatches.last().getMethodCalled().definingClass
        val enterWrapper = mutableClassDefBy(fullscreenClass).methods.single { it.name == "patch_enterFullscreen" }
        val enterMethod = enterWrapper.getInstruction<ReferenceInstruction>(0).reference as MethodReference
        val portraitEnter = Fingerprint(
            definingClass = fullscreenClass,
            name = enterMethod.name,
            parameters = listOf(),
            returnType = "V",
            filters = listOf(methodCall(
                definingClass = fullscreenClass,
                opcode = Opcode.INVOKE_DIRECT,
                parameters = listOf("Z"),
                returnType = "V",
            )),
        ).instructionMatches.single().getMethodCalled()
        mutableClassDefBy(fullscreenClass).apply {
            methods.remove(enterWrapper)
            methods.add(ImmutableMethod(
                type, enterWrapper.name, listOf(), "V", enterWrapper.accessFlags,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(0, """
                    const/4 v0, 0x0
                    invoke-direct { p0, v0 }, $portraitEnter
                    return-void
                """)
            })
        }

        // Let YouTube initialize its controller fields and listeners together with
        // the views. Inflating its ViewStubs directly leaves native ownership broken.
        val initializeControls = PlayerTopControlsInflateFingerprint.method
        val overlay = ControlsOverlayFingerprint
        check(overlay.method.definingClass == initializeControls.definingClass)
        mutableClassDefBy(initializeControls.definingClass).apply {
            interfaces.add(CONTROLS_INTERFACE)
            methods.add(
                ImmutableMethod(
                    type, "patch_initializeControls", listOf(), "V",
                    AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                    null, null, MutableMethodImplementation(1),
                ).toMutable().apply {
                    addInstructions(0, """
                        invoke-virtual { p0 }, $initializeControls
                        return-void
                    """)
                }
            )
        }
        overlay.method.apply {
            val index = overlay.instructionMatches.last().index
            val root = getInstruction<OneRegisterInstruction>(index).registerA
            val owner = findFreeRegister(index + 1, root)
            addInstructions(index + 1, """
                move-object/from16 v$owner, p0
                invoke-static { v$owner, v$root }, $EXTENSION_CLASS->setNativeControls(${CONTROLS_INTERFACE}Landroid/view/View;)V
            """)
        }

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
