package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class SetGroupIconUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, iconId: String): Result<Unit> =
        groupRepository.setGroupIcon(groupId, iconId)
}
