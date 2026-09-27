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
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.patches.youtube.layout.sponsorblock.ControlsOverlayFingerprint
import app.morphe.patches.youtube.layout.captions.autoCaptionsPatch
import app.morphe.patches.youtube.misc.playercontrols.PlayerTopControlsInflateFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
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

private const val CAPTIONS_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/RokidControlsPatch$NativeCaptions;"

private const val CAPTION_TRACK_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/RokidControlsPatch$NativeCaptionTrack;"

private fun MutableClass.addPatchMethod(
    name: String,
    parameters: List<String>,
    returnType: String,
    registers: Int,
    smali: String,
) {
    methods.add(
        ImmutableMethod(
            type, name, parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null, null, MutableMethodImplementation(registers),
        ).toMutable().apply { addInstructions(0, smali) }
    )
}

@Suppress("unused")
val rokidControlsPatch = bytecodePatch(
    name = "Rokid controls",
    description = "Turns YouTube into a Rokid glasses app driven by the touchpad or an R08 ring: " +
        "one-card feed, sections and search, player rail, and speed, quality and caption options.",
    // This fork exists for the glasses, so the patch is part of the default selection.
    default = true,
) {
    dependsOn(
        swipeControlsPatch,
        videoInformationPatch,
        videoIdPatch,
        openVideosFullscreenPatch,
        playerTypeHookPatch,
        engagementPanelHookPatch,
        resourceMappingPatch,
        autoCaptionsPatch,
    )

    compatibleWith(ROKID_YOUTUBE_COMPATIBILITY)

    execute {
        stripRokidReminders()
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

        // Captions through YouTube's subtitles controller, the same calls as its
        // captions menu: track list, shown track, preferred-track selection.
        val setSubtitleTrack = SetSubtitleTrackFingerprint.method
        val captionsClass = setSubtitleTrack.definingClass
        val trackType = setSubtitleTrack.parameterTypes[0].toString()
        val reasonType = setSubtitleTrack.parameterTypes[1].toString()
        val preferredReason = mutableClassDefBy(reasonType).methods.single { it.name == "<clinit>" }
            .implementation!!.instructions.toList().let { instructions ->
                val name = instructions.indexOfFirst {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "PREFERRED_TRACK"
                }
                check(name >= 0) { "PREFERRED_TRACK not found in $reasonType" }
                val store = instructions.drop(name).first { it.opcode == Opcode.SPUT_OBJECT }
                (store as ReferenceInstruction).reference as FieldReference
            }
        val trackMenu = Fingerprint(
            definingClass = captionsClass,
            returnType = "Ljava/util/List;",
            parameters = listOf(),
            filters = listOf(resourceLiteral(ResourceType.STRING, "turn_off_subtitles")),
        ).method
        val trackIsOff = Fingerprint(
            definingClass = trackType,
            returnType = "Z",
            parameters = listOf(),
            filters = listOf(string("DISABLE_CAPTIONS_OPTION")),
        ).method
        val trackIsAutoTranslate = Fingerprint(
            definingClass = trackType,
            returnType = "Z",
            parameters = listOf(),
            filters = listOf(string("AUTO_TRANSLATE_CAPTIONS_OPTION")),
        ).method
        mutableClassDefBy(trackType).apply {
            interfaces.add(CAPTION_TRACK_INTERFACE)
            addPatchMethod("patch_isCaptionsOff", listOf(), "Z", 2, """
                invoke-virtual { p0 }, $trackIsOff
                move-result v0
                return v0
            """)
            addPatchMethod("patch_isAutoTranslate", listOf(), "Z", 2, """
                invoke-virtual { p0 }, $trackIsAutoTranslate
                move-result v0
                return v0
            """)
        }
        mutableClassDefBy(captionsClass).apply {
            val selectTrack = methods.single {
                it.returnType == "V" && it.parameterTypes.map(Any::toString) == listOf(trackType, reasonType)
            }
            // The shown track is the public one; the private field is the last logged selection.
            val shownTrack = fields.single {
                it.type == trackType && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                    !AccessFlags.STATIC.isSet(it.accessFlags)
            }
            interfaces.add(CAPTIONS_INTERFACE)
            addPatchMethod("patch_getCaptionTracks", listOf(), "Ljava/util/List;", 2, """
                invoke-virtual { p0 }, $trackMenu
                move-result-object v0
                return-object v0
            """)
            addPatchMethod("patch_getCaptionTrack", listOf(), "Ljava/lang/Object;", 2, """
                iget-object v0, p0, $shownTrack
                return-object v0
            """)
            addPatchMethod("patch_setCaptionTrack", listOf("Ljava/lang/Object;"), "V", 3, """
                check-cast p1, $trackType
                sget-object v0, $preferredReason
                invoke-virtual { p0, p1, v0 }, $selectTrack
                return-void
            """)
            // Register right after the super constructor, while p0 surely still holds this.
            methods.filter { it.name == "<init>" }.forEach { init ->
                val superCall = init.implementation!!.instructions.indexOfFirst {
                    it.opcode == Opcode.INVOKE_DIRECT &&
                        ((it as ReferenceInstruction).reference as MethodReference).let { ref ->
                            ref.name == "<init>" && ref.definingClass == superclass
                        }
                }
                check(superCall >= 0) { "No super constructor call in $captionsClass" }
                init.addInstruction(
                    superCall + 1,
                    "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->setNativeCaptions($CAPTIONS_INTERFACE)V",
                )
            }
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
