package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.BoardRepository
import javax.inject.Inject

class SetBoardAnnouncementUseCase @Inject constructor(
    private val boardRepository: BoardRepository
) {
    suspend operator fun invoke(groupId: String, messageId: String, isAnnouncement: Boolean) =
        boardRepository.setAnnouncement(groupId, messageId, isAnnouncement)
}
