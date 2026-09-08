package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class UnblockGroupMemberUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    /**
     * Desbloquea a [targetUserId] del grupo [groupId] vía la Cloud Function
     * `unblockGroupMember`. Solo remueve el ID de `blockedIds`.
     */
    suspend operator fun invoke(groupId: String, targetUserId: String) =
        groupRepository.unblockGroupMember(groupId, targetUserId)
}
