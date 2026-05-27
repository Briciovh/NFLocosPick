package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(name: String, creatorUserId: String): Group =
        groupRepository.createGroup(name.trim(), creatorUserId)
}
