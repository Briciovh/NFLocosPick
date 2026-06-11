package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.BoardRepository
import javax.inject.Inject

class UpdateBoardMessageUseCase @Inject constructor(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(groupId: String, messageId: String, newContent: String) =
        boardRepository.updateMessage(groupId, messageId, newContent)
}
