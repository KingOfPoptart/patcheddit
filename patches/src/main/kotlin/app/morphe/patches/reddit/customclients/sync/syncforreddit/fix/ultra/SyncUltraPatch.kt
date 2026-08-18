/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.ultra

import app.morphe.patches.reddit.customclients.AppCompatibility

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val syncUltraPatch = bytecodePatch(
    name = "Unlock Sync Ultra",
    description = "Unlocks Sync Ultra Lifetime.",
) {
    compatibleWith(*AppCompatibility.SyncForReddit)

    execute {
        // return true when when checking for Sync Ultra subscription
        UltraHelperFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )

        // return Sync Ultra Lifetime when checking for subscription type
        SyncUltraSubTypeFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "Sync Ultra Lifetime"
                return-object v0
            """
        )

        // return true when checking if user is an ultra lifetime member
        UltraLifetimeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """
        )
    }
}
