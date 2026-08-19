package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.AppCompatibility
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val hideBrokenLinkPreviewPatch = bytecodePatch(
    name = "Hide broken link previews",
    description = "Sync's website preview feature for outbound comment/selftext links depends" +
        " on Sync's own backend (ap.syncforreddit.com), which is permanently offline now that" +
        " the app is unmaintained -- every attempt fails with an HTTP 401. Rather than showing a" +
        " broken-image placeholder box for every such link, this skips drawing the failed" +
        " preview's background and icon, leaving just the plain link/caption text Sync already" +
        " renders around it.",
    default = true
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        websitePreviewSpanDrawFingerprint.method.apply {
            val instructions = this.instructions.toList()

            // The placeholder background tile drawn behind both the loading spinner and
            // the broken-image icon.
            val drawRectIndex = instructions.indexOfFirst { instr ->
                (instr as? ReferenceInstruction)?.reference?.let { ref ->
                    ref is MethodReference && ref.name == "drawRect"
                } == true
            }
            check(drawRectIndex != -1) { "Could not find the placeholder background drawRect() call" }

            // Where the (skipped, since the loaded drawable is null on failure) real-bitmap
            // drawing logic starts -- falls through harmlessly to the caption-drawing tail
            // below it when there's nothing to draw, which is exactly what we want to keep.
            val bitmapCheckIndex = instructions.indexOfFirst { instr ->
                (instr as? ReferenceInstruction)?.reference?.let { ref ->
                    ref is TypeReference && ref.type == "Landroid/graphics/drawable/BitmapDrawable;"
                } == true
            }
            check(bitmapCheckIndex != -1) { "Could not find the BitmapDrawable instance-of check" }
            // Land one instruction earlier, at the "iget-object ...->r:Drawable;" read that
            // feeds the instance-of check -- jumping straight to the instance-of itself skips
            // that load, leaving its register without a verifiable reference type on this
            // branch's incoming edge (verifier rejects the whole class: "instance-of on
            // non-reference"), even though r is always null here and the check is a no-op.
            val resumeInstruction = getInstruction(bitmapCheckIndex - 1)

            addInstructionsWithLabels(
                drawRectIndex,
                """
                    iget-boolean v0, p0, Lnb/h;->D:Z
                    if-eqz v0, :loadNotFailed
                    goto :skipPlaceholder
                    :loadNotFailed
                """,
                ExternalLabel("skipPlaceholder", resumeInstruction)
            )
        }
    }
}
