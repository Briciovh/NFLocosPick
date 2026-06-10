package com.softeen.nflocospicks.domain.model

data class BoardMessage(
    val id: String = "",
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val content: String,
    val timestamp: Long,
    val editedAt: Long? = null,
    val isAnnouncement: Boolean = false
)
