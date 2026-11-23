package com.atlasglobe.app.data

data class Post(
    val id: String, val authorId: String, val authorName: String, val authorPhoto: String?,
    val official: Boolean, val kind: String,            // "image" | "text" | "video"
    val text: String, val mediaPath: String?, val createdAt: Long,
    val likes: Int, val likedByMe: Boolean, val saved: Boolean,
    val repostFrom: String?, val synced: Boolean, val cert: Int = 0, val mediaUrl: String? = null
)

data class Story(
    val id: String, val authorId: String, val authorName: String, val authorPhoto: String?,
    val mediaPath: String, val createdAt: Long, val likedByMe: Boolean, val viewed: Boolean, val synced: Boolean
)

data class AppNotification(val id: String, val type: String, val title: String, val text: String,
                           val createdAt: Long, val read: Boolean)

data class Announcement(val id: String, val title: String, val body: String, val whatsapp: String = "", val telegram: String = "", val support: String = "")
