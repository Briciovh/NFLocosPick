package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.ScoringRepository
import javax.inject.Inject

class ScoreWeekPicksUseCase @Inject constructor(
    private val scoringRepository: ScoringRepository
) {
    /**
     * Dispara el puntuado de la semana actual para [groupId] vía Cloud Function.
     * Retorna el número de picks recién puntuados.
     */
    suspend operator fun invoke(groupId: String): Int = scoringRepository.scoreWeek(groupId)
}
