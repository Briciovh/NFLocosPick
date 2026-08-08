package com.softeen.nflocospicks.domain.model

data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val photoUrl: String?,
    val regularPoints: Int,
    val preseasonPoints: Int,
    val weeklyBreakdown: Map<String, Int>,
    val rank: Int
)

private const val PRESEASON_WEEK_MARKER = "-pre-week-"

/** Filtra un weeklyBreakdown dejando solo las claves que correspondan a [seasonType]. */
fun Map<String, Int>.filterBySeasonType(seasonType: SeasonType): Map<String, Int> =
    filterKeys { weekId ->
        val isPreseasonWeek = weekId.contains(PRESEASON_WEEK_MARKER)
        if (seasonType == SeasonType.PRESEASON) isPreseasonWeek else !isPreseasonWeek
    }

/** Suma un weeklyBreakdown filtrando por si la clave corresponde a semana de pre-temporada. */
fun Map<String, Int>.sumBySeasonType(seasonType: SeasonType): Int =
    filterBySeasonType(seasonType).values.sum()
