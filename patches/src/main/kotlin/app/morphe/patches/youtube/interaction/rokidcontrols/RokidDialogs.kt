/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.interaction.rokidcontrols

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val DIALOG_CONTROLLER = "Lapp/morphe/extension/youtube/rokid/RokidDialogController;"

internal fun hookRokidDialogs(customDialog: MutableClass) {
    val buttons = customDialog.methods.single { it.name == "addButtons" }
    val instructions = buttons.implementation!!.instructions.toList()
    // CustomDialog creates Neutral, Cancel, then OK, before laying them out in rows or columns.
    val positive = instructions.indexOfLast {
        ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "createButton"
    }
    check(positive >= 0 && instructions[positive + 1].opcode == Opcode.MOVE_RESULT_OBJECT)
    val button = (instructions[positive + 1] as OneRegisterInstruction).registerA
    buttons.addInstruction(positive + 2,
        "invoke-static/range { v$button .. v$button }, $DIALOG_CONTROLLER->markPositiveButton(Landroid/widget/Button;)V")

    // The shorter overload delegates to this one; register each dialog only once.
    val create = customDialog.methods.filter { it.name == "create" && it.returnType == "Landroid/util/Pair;" }
        .maxBy { it.parameterTypes.size }
    val returns = create.implementation!!.instructions.toList().withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
    check(returns.isNotEmpty())
    returns.asReversed().forEach { (index, instruction) ->
        val pair = (instruction as OneRegisterInstruction).registerA
        create.addInstruction(index,
            "invoke-static/range { v$pair .. v$pair }, $DIALOG_CONTROLLER->prepare(Landroid/util/Pair;)V")
    }
}
