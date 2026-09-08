package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class RemoveGroupMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    /**
     * Quita a [targetUserId] del grupo [groupId] y opcionalmente lo bloquea vía la
     * Cloud Function `removeGroupMember`. Oculta su standing del leaderboard (`hidden: true`).
     * La validación de permisos (solo el creador, nunca el grupo global, ni el creador
     * a sí mismo) vive en la función; el guard cliente en `GroupViewModel` es defensa en profundidad.
     */
    suspend operator fun invoke(groupId: String, targetUserId: String, block: Boolean) =
        groupRepository.removeGroupMember(groupId, targetUserId, block)
}
