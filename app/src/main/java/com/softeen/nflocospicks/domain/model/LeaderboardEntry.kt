package com.softeen.nflocospicks.domain.model

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val totalPoints: Int,
    val weeklyBreakdown: Map<String, Int>,
    val rank: Int
)
