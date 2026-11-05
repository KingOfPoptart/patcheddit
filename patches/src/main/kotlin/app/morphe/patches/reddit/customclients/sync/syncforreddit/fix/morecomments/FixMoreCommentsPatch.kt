package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/MoreCommentsExtension;"

@Suppress("unused")
val fixMoreCommentsPatch = bytecodePatch(
    name = "Fix \"Load more comments\"",
    description = "Fixes \"Error loading more comments\". Reddit now blocks the endpoint Sync used, " +
        "so hidden comments are loaded from the comment tree instead.",
    default = true,
) {
    dependsOn(ExtensionPatches.Sync)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // Remember which comment the tapped stub hangs under (morechildren didn't need it).
        requestMoreCommentsFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->setPendingParent(Lxa/d;)V"
        )

        moreCommentsRequestFingerprint.method.apply {
            // Just before super(context, POST, url, ...): swap the URL and make it a GET.
            // Registers there: v2 = method, v3 = url, v7 = link id (p3), v8 = sort (p8), p5 = children.
            val superIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT_RANGE &&
                    (this as ReferenceInstruction).reference.let {
                        it is MethodReference && it.name == "<init>" && it.definingClass == "Lx8/a;"
                    }
            }
            val superCall = getInstruction<RegisterRangeInstruction>(superIndex)
            val first = superCall.startRegister
            val methodRegister = first + 2 // first is `this`, then context
            val urlRegister = first + 3
            addInstructions(
                superIndex,
                """
                    invoke-static { v$urlRegister, v7, p5, v8 }, $EXTENSION_CLASS_DESCRIPTOR->buildUrl(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                    const/4 v$methodRegister, 0x0
                """
            )
        }

        moreCommentsRequestFingerprint.classDef.methods.first {
            it.name == "parseNetworkResponse" && it.returnType == "Lcom/android/volley/Response;"
        }.apply {
            val stringInitIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    (this as ReferenceInstruction).reference.let {
                        it is MethodReference && it.definingClass == "Ljava/lang/String;" &&
                            it.name == "<init>" && it.parameterTypes == listOf("[B")
                    }
            }
            val bodyRegister = getInstruction<FiveRegisterInstruction>(stringInitIndex).registerC
            // v1 = this (copied from p0, which is too high a register here). v6..v8 are scratch:
            // v6 held the byte[] and is overwritten next; v7/v8 are not yet set.
            addInstructions(
                stringInitIndex + 1,
                """
                    iget-object v6, v1, Lx8/a;->s:Ljava/util/Map;
                    invoke-virtual { v1 }, Lx8/a;->getHeaders()Ljava/util/Map;
                    move-result-object v7
                    invoke-virtual { v1 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                    move-result-object v8
                    invoke-static { v$bodyRegister, v6, v7, v8 }, $EXTENSION_CLASS_DESCRIPTOR->transform(Ljava/lang/String;Ljava/util/Map;Ljava/util/Map;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$bodyRegister
                """
            )
        }
    }
}
