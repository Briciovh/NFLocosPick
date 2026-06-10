package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.domain.repository.BoardRepository
import javax.inject.Inject

class SendBoardMessageUseCase @Inject constructor(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(message: BoardMessage) =
        boardRepository.sendMessage(message)
}
