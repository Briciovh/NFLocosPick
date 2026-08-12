package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import javax.inject.Inject

class GetGamesForWeekUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(seasonType: SeasonType, weekNumber: Int): List<Game> =
        scheduleRepository.getGamesForWeek(seasonType, weekNumber)
}
