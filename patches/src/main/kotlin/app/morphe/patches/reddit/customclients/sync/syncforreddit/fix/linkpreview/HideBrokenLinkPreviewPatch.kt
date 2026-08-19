package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.AppCompatibility

@Suppress("unused")
val hideBrokenLinkPreviewPatch = bytecodePatch(
    name = "Hide broken link previews",
    description = "Sync's website preview feature for outbound comment/selftext links depends" +
        " on Sync's own backend (ap.syncforreddit.com), which is permanently offline now that" +
        " the app is unmaintained -- every attempt fails with an HTTP 401. Rather than reserving" +
        " space for a preview that will never load, this makes every link ineligible for the" +
        " feature, so the link renders as plain text/link like it would if the feature were off.",
    default = true
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        websitePreviewEligibleFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )
    }
}
