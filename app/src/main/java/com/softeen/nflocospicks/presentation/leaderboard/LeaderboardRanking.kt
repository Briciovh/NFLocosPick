package com.softeen.nflocospicks.presentation.leaderboard

import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.SeasonType

/**
 * Reordena [this] por los puntos del [seasonType] seleccionado y recalcula el
 * rank (empates comparten rank), ya que el orden difiere por pestaña.
 */
fun List<LeaderboardEntry>.rankedFor(seasonType: SeasonType): List<LeaderboardEntry> {
    fun points(e: LeaderboardEntry) = if (seasonType == SeasonType.PRESEASON) e.preseasonPoints else e.regularPoints

    val sorted = sortedByDescending { points(it) }
    var currentRank = 1
    return sorted.mapIndexed { index, e ->
        if (index > 0 && points(sorted[index - 1]) > points(e)) currentRank = index + 1
        e.copy(rank = currentRank)
    }
}
