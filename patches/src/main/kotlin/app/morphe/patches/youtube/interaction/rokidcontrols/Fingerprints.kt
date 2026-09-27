/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Subtitles controller's setSubtitleTrack(track, reason, int): it logs every
 * selection. Parameters give the SubtitleTrack and selection-reason types.
 */
internal object SetSubtitleTrackFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        string("setSubtitleTrack name:", StringComparisonType.STARTS_WITH),
    ),
)
