package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/FixGalleryExtension;"

@Suppress("unused")
val fixGalleryPatch = bytecodePatch(
    name = "Fix galleries",
    description = "Fixes Reddit gallery posts failing to open (Reddit now rejects the /api/info endpoint Sync used), " +
        "and shows a thumbnail for crossposted galleries instead of a blank placeholder.",
    default = true,
) {
    dependsOn(ExtensionPatches.Sync)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // Load the gallery from /comments/{id}.json instead of /api/info.json.
        galleryRequestBuildUrlFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->buildGalleryUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                return-object p0
            """
        )

        // /comments returns [post listing, comment listing]; hand the parser just the post listing.
        galleryRequestParseResponseFingerprint.method.apply {
            val stringInitIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    (this as ReferenceInstruction).reference.let {
                        it is MethodReference && it.definingClass == "Ljava/lang/String;" &&
                            it.name == "<init>" && it.parameterTypes == listOf("[B")
                    }
            }
            val stringRegister = getInstruction<FiveRegisterInstruction>(stringInitIndex).registerC
            addInstructions(
                stringInitIndex + 1,
                """
                    invoke-static { v$stringRegister }, $EXTENSION_CLASS_DESCRIPTOR->unwrapGalleryResponse(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$stringRegister
                """
            )
        }
    
        // Crossposted galleries: copy the parent post's first image in as the preview.
        postParserFingerprint.method.apply {
            val galleryUrlIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.CONST_STRING &&
                    ((this as ReferenceInstruction).reference as StringReference).string == "?is_gallery=true"
            }
            val postJsonRegister = getInstruction<FiveRegisterInstruction>(
                indexOfFirstInstructionReversedOrThrow(galleryUrlIndex) {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                        ((this as ReferenceInstruction).reference as MethodReference).let {
                            it.definingClass == "Lorg/json/JSONObject;" && it.name == "optJSONArray"
                        }
                }
            ).registerC
            val putUrlIndex = indexOfFirstInstructionOrThrow(galleryUrlIndex) {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    ((this as ReferenceInstruction).reference as MethodReference).let {
                        it.definingClass == "Landroid/content/ContentValues;" && it.name == "put" &&
                            it.parameterTypes == listOf("Ljava/lang/String;", "Ljava/lang/String;")
                    }
            }
            val contentValuesRegister = getInstruction<FiveRegisterInstruction>(putUrlIndex).registerC
            addInstructions(
                putUrlIndex + 1,
                """
                    invoke-static { v$contentValuesRegister, v$postJsonRegister }, $EXTENSION_CLASS_DESCRIPTOR->addCrosspostGalleryPreview(Landroid/content/ContentValues;Lorg/json/JSONObject;)V
                """
            )
        }
}
}
