package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.JoinGroupResult
import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class JoinGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(inviteCode: String, userId: String): JoinGroupResult =
        groupRepository.joinGroup(inviteCode.trim().uppercase(), userId)
}
