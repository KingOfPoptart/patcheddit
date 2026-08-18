/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.redditvideolink

import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.ExtensionPatches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/syncforreddit/RedditVideoLinkExtension;"

@Suppress("unused")
val redditVideoLinkPatch = bytecodePatch(
    name = "Fix Reddit Video Links",
    description = "Fixes reddit video links (reddit.com/link/.../video) so they open in the native video player.",
    default = true,
) {
    dependsOn(ExtensionPatches.Sync)
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        linkHandlerOpenLinkFingerprint.method.addInstructions(
            0,
            """
                # p3 holds the URL string in the LinkHelper.openLink method
                invoke-static { p3 }, $EXTENSION_CLASS_DESCRIPTOR->fixVideoLink(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p3
            """
        )
    }
}
