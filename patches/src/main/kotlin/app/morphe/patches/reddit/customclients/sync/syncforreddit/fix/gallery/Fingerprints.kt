package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// OAuthGalleryRequest.b(String): builds the gallery URL.
internal val galleryRequestBuildUrlFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("https://oauth.reddit.com/api/info.json?id=t3_")
)

// OAuthGalleryRequest.parseNetworkResponse(NetworkResponse).
internal val galleryRequestParseResponseFingerprint = Fingerprint(
    name = "parseNetworkResponse",
    strings = listOf("Could not load gallery"),
    // Other request classes share the same error string.
    custom = { _, classDef -> classDef.sourceFile == "OAuthGalleryRequest.java" }
)

// PostParserHelper.a(...): turns post JSON into ContentValues. Its crosspost-of-a-gallery branch
// never sets a preview image.
internal val postParserFingerprint = Fingerprint(
    returnType = "V",
    strings = listOf("crosspost_parent_list", "post_cross_origin", "?is_gallery=true"),
    custom = { _, classDef -> classDef.sourceFile == "PostParserHelper.java" }
)
