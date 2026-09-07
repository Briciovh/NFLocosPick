package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class DeleteGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    /**
     * Elimina por completo el grupo [groupId] vía la Cloud Function `deleteGroup`.
     * La validación de permisos (solo el creador, nunca el grupo global) vive en la
     * función; el guard cliente en `GroupViewModel` es defensa en profundidad.
     */
    suspend operator fun invoke(groupId: String) =
        groupRepository.deleteGroup(groupId)
}
