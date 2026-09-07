package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class RenameGroupUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, newName: String) =
        groupRepository.renameGroup(groupId, newName.trim())
}
