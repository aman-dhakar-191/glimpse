package com.glimpse.app.data.model

import com.google.firebase.database.Exclude

data class Message @JvmOverloads constructor(
    val authorUid: String = "",
    val authorName: String = "",
    val type: String = "text",
    val content: String = "",
    val photoUrl: String = "",
    val caption: String = "",
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val expiresAt: Long = 0,
    // 0 = not a time capsule. Otherwise, content/photoUrl/caption stay
    // hidden (both in-app and on the widget) until this moment — see
    // isLocked below, gating display in MessageHistoryScreen and on the widget.
    val unlockAt: Long = 0,
    // The Firebase push key of this message under shared/messages — not
    // stored as a value field itself (Exclude keeps it out of setValue()
    // writes), populated by the caller from the DataSnapshot's key.
    @get:Exclude val id: String = ""
) {
    @get:Exclude
    val isLocked: Boolean
        get() = unlockAt > 0 && unlockAt > System.currentTimeMillis()

    // Both "photo" and "drawing" render as an image (photoUrl + optional
    // caption) — everywhere that distinction matters (both widgets,
    // MessageHistoryScreen) checks this instead of the raw type string, so
    // a future third image-like type only needs to change it here.
    @get:Exclude
    val isImage: Boolean
        get() = type == "photo" || type == "drawing"

    // Deliberately NOT folded into isImage above — video reuses the same
    // photoUrl field as the actual media URL (renaming it app-wide felt
    // riskier than just stretching its meaning here), but needs its own
    // playback UI everywhere isImage's Coil-based AsyncImage rendering is
    // used, since that can't decode a video file as a bitmap.
    @get:Exclude
    val isVideo: Boolean
        get() = type == "video"
}
