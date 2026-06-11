package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.BoardRepository
import javax.inject.Inject

class DeleteBoardMessageUseCase @Inject constructor(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(groupId: String, messageId: String) =
        boardRepository.deleteMessage(groupId, messageId)
}
