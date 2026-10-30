package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/FixGalleryExtension;"

@Suppress("unused")
val fixGalleryPatch = bytecodePatch(
    name = "Fix galleries",
    description = "Fixes Reddit gallery posts failing to open. Reddit now rejects the /api/info endpoint Sync used to load galleries.",
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
    }
}
