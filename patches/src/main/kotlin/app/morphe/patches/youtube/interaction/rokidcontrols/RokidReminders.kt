/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

/** Remove reminder subscriptions, before they can pause playback or open a blocking panel. */
internal fun BytecodePatchContext.stripRokidReminders() {
    // These controller names are pinned to the patch's sole supported APK, 21.04.223.
    // Validate their shared base and distinctive subscription code before replacing it.
    val bedtime = Fingerprint(
        definingClass = "Lhpd;",
        parameters = emptyList(),
        returnType = "V",
        filters = listOf(
            string("Egl0aGVtZS1zZXQgSygB"),
            methodCall("Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V"),
        ),
    ).method
    val watchBreak = Fingerprint(
        definingClass = "Lhrd;",
        parameters = emptyList(),
        returnType = "V",
        filters = listOf(
            literal(1073820296L),
            methodCall("Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V"),
        ),
    ).method
    check(mutableClassDefBy(bedtime.definingClass).superclass == "Lhoy;")
    check(mutableClassDefBy(watchBreak.definingClass).superclass == "Lhoy;")
    eraseReminderSubscription(bedtime)
    eraseReminderSubscription(watchBreak)

    // Remove both preferences after YouTube finishes creating/replacing its V1/V2 rows.
    // Do not erase the shared reminder base: data-usage alerts also use it.
    val general = Fingerprint(
        definingClass = "Lcom/google/android/apps/youtube/app/settings/GeneralPrefsFragment;",
        parameters = emptyList(),
        returnType = "V",
        filters = listOf(resourceLiteral(ResourceType.XML, "general_prefs")),
    ).method
    val removePreference = Fingerprint(
        definingClass = general.definingClass,
        parameters = listOf("Ljava/lang/CharSequence;"),
        returnType = "V",
    ).method
    general.implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_VOID }
        .map { it.index }.reversed().forEach { index ->
            val key = general.findFreeRegister(index)
            val owner = general.findFreeRegister(index, key)
            general.addInstructions(index, """
                move-object/from16 v$owner, p0
                const-string v$key, "bedtime_reminder_toggle"
                invoke-direct { v$owner, v$key }, $removePreference
                const-string v$key, "watch_break_frequency_picker_preference"
                invoke-direct { v$owner, v$key }, $removePreference
            """)
        }
}

private fun BytecodePatchContext.eraseReminderSubscription(method: MutableMethod) {
    check(method.parameterTypes.isEmpty() && method.returnType == "V")
    val owner = mutableClassDefBy(method.definingClass)
    check(owner.methods.remove(method))
    owner.methods.add(ImmutableMethod(
        method.definingClass, method.name, method.parameters, method.returnType,
        method.accessFlags, method.annotations, method.hiddenApiRestrictions,
        MutableMethodImplementation(1),
    ).toMutable().apply { addInstructions(0, "return-void") })
}
