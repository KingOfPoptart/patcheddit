package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// OAuthCommentsMoreRequest constructor: builds the POST to api/morechildren.
internal val moreCommentsRequestFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    strings = listOf("api/morechildren?always_show_media=1&profile_img=true", "children"),
    custom = { _, classDef -> classDef.sourceFile == "OAuthCommentsMoreRequest.java" }
)

// CommentsPresenterImpl.V(Post): user tapped a "load more comments" stub.
internal val requestMoreCommentsFingerprint = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Lxa/d;"),
    strings = listOf("Requesting more: "),
    custom = { _, classDef -> classDef.sourceFile == "CommentsPresenterImpl.java" }
)
