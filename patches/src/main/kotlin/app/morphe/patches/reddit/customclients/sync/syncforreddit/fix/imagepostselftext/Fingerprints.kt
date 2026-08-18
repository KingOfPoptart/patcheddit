/*
 * Ported from humzakh/HK-Morphe-Patches (https://github.com/humzakh/HK-Morphe-Patches), GPLv3.
 */
package app.morphe.patches.reddit.customclients.sync.syncforreddit.fix.imagepostselftext

import app.morphe.patcher.Fingerprint

internal val CardSelftextPreviewTextViewExpandedBindFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/views/posts/cards/CardSelftextPreviewTextView;",
    name = "K",
    parameters = listOf("Lxa/d;", "Z"),
    returnType = "V"
)

internal val SimpleSelftextPreviewTextViewExpandedBindFingerprint = Fingerprint(
    definingClass = "Lcom/laurencedawson/reddit_sync/ui/views/posts/simple/SimpleSelftextPreviewTextView;",
    name = "L",
    parameters = listOf("Lxa/d;", "Z"),
    returnType = "V"
)
