package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.repository.BoardRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchBoardMessagesUseCase @Inject constructor(
    private val boardRepository: BoardRepository
) {
    operator fun invoke(groupId: String): Flow<List<BoardMessage>> =
        boardRepository.watchMessages(groupId)
}
