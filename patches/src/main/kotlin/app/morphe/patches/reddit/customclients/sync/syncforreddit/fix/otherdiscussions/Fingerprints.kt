/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.otherdiscussions

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val otherDiscussionsBuildUrlFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    strings = listOf("submit.json?url=")
)
