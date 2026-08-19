package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Lwc/q; is WebsitePreviewHelper.java. Its a(String) method decides whether a given
// outbound comment/selftext link is eligible for Sync's "website preview" feature --
// eligible links get wrapped in an Lnb/h; ReplacementSpan that renders a thumbnail
// fetched through Sync's own backend (ap.syncforreddit.com), which is permanently
// offline now that the app is unmaintained (every request returns HTTP 401).
internal val websitePreviewEligibleFingerprint = Fingerprint(
    definingClass = "Lwc/q;",
    name = "a",
    returnType = "Z",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Ljava/lang/String;")
)
