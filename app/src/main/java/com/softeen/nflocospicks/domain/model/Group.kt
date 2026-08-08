package com.softeen.nflocospicks.domain.model

data class Group(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdBy: String,          // userId del creador
    val memberIds: List<String>,
    val photoUrl: String? = null,   // foto subida por el creador; mutuamente excluyente con iconId
    val iconId: String? = null      // ícono predefinido elegido por el creador
)
