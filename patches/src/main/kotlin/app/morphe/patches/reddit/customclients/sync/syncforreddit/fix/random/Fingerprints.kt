/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.random

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val randomRequestBuildUrlFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    strings = listOf("r/random.json", "r/randnsfw.json"),
    custom = { _, classDef -> classDef.sourceFile == "RandomRequest.java" }
)

internal val parseRandomNetworkResponseFingerprint = Fingerprint(
    name = "parseNetworkResponse",
    returnType = "Lcom/android/volley/Response;",
    custom = { _, classDef -> classDef.sourceFile == "RandomRequest.java" }
)
