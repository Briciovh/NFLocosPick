package com.softeen.nflocospicks.data.remote.espn

import com.google.common.truth.Truth.assertThat
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.SeasonType
import org.junit.Test

class EspnMapperTest {

    @Test
    fun `toDomain maps scoreboard response correctly`() {
        // Given
        val response = EspnScoreboardResponse(
            week = EspnWeek(number = 1),
            season = EspnSeason(type = 2),
            events = listOf(
                EspnEvent(
                    id = "1",
                    date = "2025-09-07T17:00Z",
                    competitions = listOf(
                        EspnCompetition(
                            competitors = listOf(
                                EspnCompetitor(
                                    homeAway = "home",
                                    score = "24",
                                    team = EspnTeam(displayName = "Kansas City Chiefs", abbreviation = "KC"),
                                    records = listOf(EspnRecord(name = "overall", summary = "1-0"))
                                ),
                                EspnCompetitor(
                                    homeAway = "away",
                                    score = "20",
                                    team = EspnTeam(displayName = "Las Vegas Raiders", abbreviation = "LV"),
                                    records = listOf(EspnRecord(name = "overall", summary = "0-1"))
                                )
                            ),
                            status = EspnStatus(
                                type = EspnStatusType(name = "STATUS_FINAL", completed = true)
                            )
                        )
                    )
                )
            )
        )

        // When
        val domainGames = response.toDomain()

        // Then
        assertThat(domainGames).hasSize(1)
        val game = domainGames[0]
        assertThat(game.id).isEqualTo("1")
        assertThat(game.homeTeam).isEqualTo("Kansas City Chiefs")
        assertThat(game.awayTeam).isEqualTo("Las Vegas Raiders")
        assertThat(game.homeTeamAbbr).isEqualTo("KC")
        assertThat(game.awayTeamAbbr).isEqualTo("LV")
        assertThat(game.homeScore).isEqualTo(24)
        assertThat(game.awayScore).isEqualTo(20)
        assertThat(game.status).isEqualTo(GameStatus.FINAL)
        assertThat(game.weekId).isEqualTo("2025-week-01")
        assertThat(game.seasonType).isEqualTo(SeasonType.REGULAR)
        assertThat(game.homeTeamRecord).isEqualTo("1-0")
        assertThat(game.awayTeamRecord).isEqualTo("0-1")
    }

    @Test
    fun `toDomain handles malformed events gracefully`() {
        // Given
        val response = EspnScoreboardResponse(
            week = EspnWeek(number = 1),
            season = EspnSeason(type = 2),
            events = listOf(
                EspnEvent(
                    id = "malformed",
                    date = "invalid-date",
                    competitions = emptyList() // Will cause exception
                )
            )
        )

        // When
        val domainGames = response.toDomain()

        // Then
        assertThat(domainGames).isEmpty()
    }

    @Test
    fun `toDomain maps preseason weekId with pre- segment`() {
        val response = EspnScoreboardResponse(
            week = EspnWeek(number = 1),
            season = EspnSeason(type = 1),
            events = listOf(singleEvent(id = "2", homeAbbr = "KC", awayAbbr = "LV"))
        )

        val domainGames = response.toDomain()

        assertThat(domainGames).hasSize(1)
        assertThat(domainGames[0].weekId).isEqualTo("2025-pre-week-01")
        assertThat(domainGames[0].seasonType).isEqualTo(SeasonType.PRESEASON)
    }

    @Test
    fun `toDomain maps postseason without weekId prefix`() {
        val response = EspnScoreboardResponse(
            week = EspnWeek(number = 1),
            season = EspnSeason(type = 3),
            events = listOf(singleEvent(id = "3", homeAbbr = "KC", awayAbbr = "LV"))
        )

        val domainGames = response.toDomain()

        assertThat(domainGames).hasSize(1)
        // Post-temporada mantiene el formato sin prefijo — misma ambigüedad de hoy,
        // fuera de alcance de la separación pre-temporada/temporada regular.
        assertThat(domainGames[0].weekId).isEqualTo("2025-week-01")
        assertThat(domainGames[0].seasonType).isEqualTo(SeasonType.POSTSEASON)
    }

    private fun singleEvent(id: String, homeAbbr: String, awayAbbr: String) = EspnEvent(
        id = id,
        date = "2025-08-07T17:00Z",
        competitions = listOf(
            EspnCompetition(
                competitors = listOf(
                    EspnCompetitor(
                        homeAway = "home",
                        score = null,
                        team = EspnTeam(displayName = homeAbbr, abbreviation = homeAbbr)
                    ),
                    EspnCompetitor(
                        homeAway = "away",
                        score = null,
                        team = EspnTeam(displayName = awayAbbr, abbreviation = awayAbbr)
                    )
                ),
                status = EspnStatus(type = EspnStatusType(name = "STATUS_SCHEDULED", completed = false))
            )
        )
    )
}
