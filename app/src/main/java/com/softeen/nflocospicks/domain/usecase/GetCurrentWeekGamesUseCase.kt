package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import javax.inject.Inject

class GetCurrentWeekGamesUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(groupId: String): List<Game> =
        scheduleRepository.getCurrentWeekGames(groupId)
}
