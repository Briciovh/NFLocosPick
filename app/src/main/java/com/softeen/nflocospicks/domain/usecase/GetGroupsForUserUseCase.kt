package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGroupsForUserUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    operator fun invoke(userId: String): Flow<List<Group>> =
        groupRepository.getGroupsForUser(userId)
}
