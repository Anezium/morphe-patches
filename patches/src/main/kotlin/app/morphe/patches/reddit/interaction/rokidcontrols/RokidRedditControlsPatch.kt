/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.patches.reddit.interaction.rokidcontrols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.reddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.misc.fix.signature.spoofSignaturePatch
import app.morphe.util.setExtensionIsPatchIncluded
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION = "Lapp/morphe/extension/reddit/rokid/RokidRedditPatch;"
private const val CONTINUATION = "Lapp/morphe/extension/reddit/rokid/nativebridge/NativeContinuation;"
private const val NATIVE_CONTINUATION = "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"
private const val NATIVE_COMPLETION = "Lul3/a;"

private val rokidRedditMarkerPatch = resourcePatch {
    execute {
        get("assets/rokid").apply { mkdirs() }.resolve("reddit.json").writeText(
            """{"schema":1,"package":"com.reddit.frontpage","version":"2026.14.0","versionCode":2614001,"hud":"rokid-reddit-1"}"""
        )
    }
}

private fun MutableClass.addMethod(
    name: String, parameters: List<String>, result: String, registers: Int, code: String,
    flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
) {
    check(methods.none { it.name == name && it.parameterTypes.map(Any::toString) == parameters })
    methods.add(ImmutableMethod(
        type, name, parameters.map { ImmutableMethodParameter(it, null, null) }, result,
        flags, null, null, MutableMethodImplementation(registers),
    ).toMutable().apply { addInstructions(0, code) })
}

@Suppress("unused")
val rokidRedditControlsPatch = bytecodePatch(
    name = "Rokid Reddit controls",
    description = "Adapts official Reddit for a black portrait glasses HUD, single-axis navigation " +
        "and reviewed replies to the exact post or comment using the phone keyboard.",
    default = false,
) {
    dependsOn(sharedExtensionPatch, spoofSignaturePatch, rokidRedditMarkerPatch)
    compatibleWith(Compatibility(
        name = "Reddit",
        packageName = "com.reddit.frontpage",
        apkFileType = ApkFileType.APKM_REQUIRED,
        appIconColor = 0xff4500,
        signatures = setOf("970b91143813b4c9d5f3634f672c9fcaa5621b4efaaedafd6c235cbbb869736f"),
        targets = listOf(AppTarget(version = "2026.14.0", minSdk = 29)),
    ))

    execute {
        // Validate the original coroutine contract before changing the extension superclass.
        check(mutableClassDefBy(NATIVE_COMPLETION).methods.any {
            it.name == "resumeWith" && it.parameterTypes.map(Any::toString) == listOf("Ljava/lang/Object;")
        })
        check(mutableClassDefBy(NATIVE_CONTINUATION).methods.any {
            it.name == "<init>" && it.parameterTypes.map(Any::toString) == listOf(
                NATIVE_COMPLETION, "Lkotlin/coroutines/CoroutineContext;",
            )
        })
        mutableClassDefBy(CONTINUATION).apply {
            setSuperClass(NATIVE_CONTINUATION)
            val constructor = methods.single { it.name == "<init>" }
            check(constructor.parameterTypes.map(Any::toString) == listOf("Ljava/lang/Object;"))
            methods.remove(constructor)
            addMethod("<init>", listOf("Ljava/lang/Object;"), "V", 3, """
                check-cast p1, $NATIVE_COMPLETION
                sget-object v0, Lkotlin/coroutines/EmptyCoroutineContext;->INSTANCE:Lkotlin/coroutines/EmptyCoroutineContext;
                invoke-direct { p0, p1, v0 }, $NATIVE_CONTINUATION-><init>(${NATIVE_COMPLETION}Lkotlin/coroutines/CoroutineContext;)V
                return-void
            """, AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value)
        }
        val suspendType = "Lkotlin/coroutines/jvm/internal/SuspendLambda;"
        check(mutableClassDefBy(suspendType).methods.any {
            it.name == "<init>" && it.parameterTypes.map(Any::toString) == listOf("I", NATIVE_COMPLETION)
        })
        mutableClassDefBy("Lapp/morphe/extension/reddit/rokid/nativebridge/NativeSuspend;").apply {
            setSuperClass(suspendType)
            methods.remove(methods.single { it.name == "<init>" })
            addMethod("<init>", listOf("Ljava/lang/Object;"), "V", 3, """
                check-cast p1, $NATIVE_COMPLETION
                const/4 v0, 0x2
                invoke-direct { p0, v0, p1 }, $suspendType-><init>(I$NATIVE_COMPLETION)V
                return-void
            """, AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value)
        }

        val capture = "$EXTENSION->capture(Ljava/lang/Object;)V"
        listOf(
            "Lhc1/t1;",
            "Lcom/reddit/session/o;",
            "Lcom/reddit/link/impl/data/repository/l;",
            "Lcom/reddit/comment/data/repository/b;",
            "Lcom/reddit/feeds/home/impl/data/paging/e;",
            "Lcom/reddit/feeds/popular/impl/data/paging/a;",
            "Lcom/reddit/feeds/latest/impl/data/a;",
        ).forEach { type ->
            mutableClassDefBy(type).methods.filter { it.name == "<init>" }.also { constructors ->
                check(constructors.isNotEmpty()) { "Missing native service constructor: $type" }
            }.forEach { constructor ->
                constructor.implementation!!.instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_VOID }
                    .map { it.index }.reversed().forEach { index ->
                        constructor.addInstructions(index, "invoke-static/range { p0 .. p0 }, $capture")
                    }
            }
        }

        val main = mutableClassDefBy("Lcom/reddit/launch/main/MainActivity;")
        mutableClassDefBy("Lcom/reddit/frontpage/FrontpageApplication;").methods.single {
            it.name == "onCreate" && it.parameterTypes.isEmpty()
        }.apply {
            addInstructions(0, "invoke-static/range { p0 .. p0 }, $EXTENSION->register(Landroid/app/Application;)V")
        }
        // Stock ComponentActivity owns a final dispatch method. Hook that method;
        // adding an override to MainActivity fails the Android class verifier.
        var inputOwner = main
        while (inputOwner.methods.none { it.name == "dispatchKeyEvent" }) {
            inputOwner = mutableClassDefBy(requireNotNull(inputOwner.superclass))
        }
        inputOwner.methods.single {
            it.name == "dispatchKeyEvent" && it.returnType == "Z" &&
                it.parameterTypes.map(Any::toString) == listOf("Landroid/view/KeyEvent;")
        }.apply {
            val scratch = findFreeRegister(0)
            addInstructionsWithLabels(0, """
                invoke-static/range { p0 .. p1 }, $EXTENSION->handleKeyEvent(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
                move-result v$scratch
                if-eqz v$scratch, :native_input
                const/4 v$scratch, 0x1
                return v$scratch
                :native_input
                nop
            """)
        }
        setExtensionIsPatchIncluded(EXTENSION)
    }
}
