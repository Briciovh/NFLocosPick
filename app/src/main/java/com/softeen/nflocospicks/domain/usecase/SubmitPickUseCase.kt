package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.repository.PickRepository
import javax.inject.Inject

class SubmitPickUseCase @Inject constructor(
    private val pickRepository: PickRepository
) {
    /**
     * Envía el pick del usuario. Lanza [IllegalStateException] si el kickoff ya pasó
     * o si el juego ya no está SCHEDULED (pick bloqueado). De lo contrario persiste
     * en Firestore.
     */
    suspend operator fun invoke(
        groupId: String,
        weekId: String,
        userId: String,
        gameId: String,
        teamAbbr: String,
        kickoffTime: Long,
        status: GameStatus
    ) {
        if (System.currentTimeMillis() >= kickoffTime || status != GameStatus.SCHEDULED) {
            throw IllegalStateException("El partido ya comenzó, no puedes cambiar tu pick")
        }
        pickRepository.submitPick(groupId, weekId, userId, gameId, teamAbbr)
    }
}
