package com.softeen.nflocospicks.domain.model

data class Standing(
    val userId: String,
    val totalPoints: Int,
    val weeklyBreakdown: Map<String, Int>   // weekId → puntos ganados esa semana
)
