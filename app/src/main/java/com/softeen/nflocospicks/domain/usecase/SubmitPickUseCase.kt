package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.PickRepository
import javax.inject.Inject

class SubmitPickUseCase @Inject constructor(
    private val pickRepository: PickRepository
) {
    /**
     * Envía el pick del usuario. Lanza [IllegalStateException] si el kickoff ya pasó
     * (pick bloqueado). De lo contrario persiste en Firestore.
     */
    suspend operator fun invoke(
        groupId: String,
        weekId: String,
        userId: String,
        gameId: String,
        teamAbbr: String,
        kickoffTime: Long
    ) {
        if (System.currentTimeMillis() >= kickoffTime) {
            throw IllegalStateException("El partido ya comenzó, no puedes cambiar tu pick")
        }
        pickRepository.submitPick(groupId, weekId, userId, gameId, teamAbbr)
    }
}
