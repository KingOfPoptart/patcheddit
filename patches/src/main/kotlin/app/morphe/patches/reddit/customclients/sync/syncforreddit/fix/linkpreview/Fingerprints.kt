package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Lnb/h; is the ReplacementSpan (also a Glide Target) that draws Sync's generic
// "website preview" box for outbound comment/selftext links -- the broken-image
// placeholder shown when its backend (ap.syncforreddit.com) fails to load a preview.
internal val websitePreviewSpanDrawFingerprint = Fingerprint(
    definingClass = "Lnb/h;",
    name = "draw",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC),
    parameters = listOf(
        "Landroid/graphics/Canvas;",
        "Ljava/lang/CharSequence;",
        "I",
        "I",
        "F",
        "I",
        "I",
        "I",
        "Landroid/graphics/Paint;"
    )
)
