/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.otherdiscussions

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/OtherDiscussionsExtension;"

@Suppress("unused")
val otherDiscussionsPatch = bytecodePatch(
    name = "Fix \"Other Discussions\"",
    description = "Fixes the \"Other Discussions\" feature.",
    default = true,
) {
    dependsOn(ExtensionPatches.Sync)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        otherDiscussionsBuildUrlFingerprint.method.apply {
            val submitMatch = otherDiscussionsBuildUrlFingerprint.stringMatches.firstOrNull {
                val instr = getInstruction<ReferenceInstruction>(it.index)
                val ref = instr.reference as? StringReference
                ref?.string == "submit.json?url="
            }

            if (submitMatch != null) {
                val instr = getInstruction<ReferenceInstruction>(submitMatch.index)
                val targetRegister = (instr as OneRegisterInstruction).registerA
                replaceInstruction(submitMatch.index, "const-string v${targetRegister}, \"api/info?url=\"")
            }

            val instructions = this.implementation?.instructions?.toList() ?: emptyList()
            val sslIndex = instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference?.let { ref ->
                    ref is StringReference && (ref.string == "https://ssl.reddit.com/" || ref.string == "https://www.reddit.com/")
                } == true
            }

            if (sslIndex != -1) {
                val sslInstr = getInstruction<ReferenceInstruction>(sslIndex)
                val targetRegister = (sslInstr as OneRegisterInstruction).registerA
                replaceInstruction(sslIndex, "const-string v${targetRegister}, \"https://oauth.reddit.com/\"")
            }
        }
    
        // Reddit now 403s /api/info?url=, so load /duplicates/{id} instead. Sync only hands the
        // link URL to this screen, so swap it for the post ID when the screen is opened.
        openOtherDiscussionsFingerprint.method.apply {
            val keyIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    (this as ReferenceInstruction).reference.let {
                        it is MethodReference && it.returnType == "Ljava/lang/String;" &&
                            it.parameterTypes == listOf("Ljava/lang/String;") && it.definingClass == "Ld7/d;"
                    }
            }
            val postGetterIndex = keyIndex - 2 // invoke-virtual {p1}, Post->J0() ; move-result-object p1
            addInstructions(
                keyIndex + 2,
                """
                    invoke-static { p1, v1 }, $EXTENSION_CLASS_DESCRIPTOR->getOtherDiscussionsKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object p1
                """
            )
            addInstructions(
                postGetterIndex,
                """
                    invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->getPostId(Lxa/d;)Ljava/lang/String;
                    move-result-object v1
                """
            )
        }

        val listingRequestClass = otherDiscussionsBuildUrlFingerprint.classDef
        val buildUrlMethod = otherDiscussionsBuildUrlFingerprint.method

        listingRequestClass.methods.first { it.name == "<init>" }.apply {
            val buildIndex = indexOfFirstInstructionOrThrow {
                (this as? ReferenceInstruction)?.reference.let {
                    it is MethodReference && it.name == buildUrlMethod.name &&
                        it.definingClass == buildUrlMethod.definingClass && it.parameterTypes == buildUrlMethod.parameterTypes
                }
            }
            val urlRegister = getInstruction<OneRegisterInstruction>(buildIndex + 1).registerA
            addInstructions(
                buildIndex + 2,
                """
                    invoke-static { v$urlRegister }, $EXTENSION_CLASS_DESCRIPTOR->fixListingUrl(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$urlRegister
                """
            )
        }

        listingRequestClass.methods.first { it.name == "parseNetworkResponse" && it.returnType == "Lcom/android/volley/Response;" }.apply {
            val stringInitIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    (this as ReferenceInstruction).reference.let {
                        it is MethodReference && it.definingClass == "Ljava/lang/String;" &&
                            it.name == "<init>" && it.parameterTypes == listOf("[B")
                    }
            }
            val bodyRegister = getInstruction<FiveRegisterInstruction>(stringInitIndex).registerC
            addInstructions(
                stringInitIndex + 1,
                """
                    invoke-static { v$bodyRegister }, $EXTENSION_CLASS_DESCRIPTOR->unwrapDuplicatesResponse(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$bodyRegister
                """
            )
        }
}
}
