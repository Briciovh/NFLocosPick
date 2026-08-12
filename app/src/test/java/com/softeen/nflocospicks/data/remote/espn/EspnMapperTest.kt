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
            events = listOf(
                EspnEvent(
                    id = "1",
                    date = "2025-09-07T17:00Z",
                    season = EspnSeason(type = 2),
                    week = EspnWeek(number = 1),
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
        assertThat(game.weekNumber).isEqualTo(1)
    }

    @Test
    fun `preseason weekId keeps the raw API week number, no HOF offset applied`() {
        // week.number=2 en la API es "PRE WK 1" en el sitio de ESPN (el Hall of
        // Fame Game es el 1), pero buildWeekId debe seguir usando el número
        // crudo — el offset de display es solo cosa de SeasonWeek.displayNumber.
        val response = EspnScoreboardResponse(
            events = listOf(
                singleEvent(id = "4", homeAbbr = "KC", awayAbbr = "LV", seasonType = 1, weekNumber = 2)
            )
        )

        val game = response.toDomain().single()

        assertThat(game.weekNumber).isEqualTo(2)
        assertThat(game.weekId).isEqualTo("2025-pre-week-02")
    }

    @Test
    fun `toDomain handles malformed events gracefully`() {
        // Given
        val response = EspnScoreboardResponse(
            events = listOf(
                EspnEvent(
                    id = "malformed",
                    date = "invalid-date",
                    season = EspnSeason(type = 2),
                    week = EspnWeek(number = 1),
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
            events = listOf(singleEvent(id = "2", homeAbbr = "KC", awayAbbr = "LV", seasonType = 1))
        )

        val domainGames = response.toDomain()

        assertThat(domainGames).hasSize(1)
        assertThat(domainGames[0].weekId).isEqualTo("2025-pre-week-01")
        assertThat(domainGames[0].seasonType).isEqualTo(SeasonType.PRESEASON)
    }

    @Test
    fun `toDomain maps postseason without weekId prefix`() {
        val response = EspnScoreboardResponse(
            events = listOf(singleEvent(id = "3", homeAbbr = "KC", awayAbbr = "LV", seasonType = 3))
        )

        val domainGames = response.toDomain()

        assertThat(domainGames).hasSize(1)
        // Post-temporada mantiene el formato sin prefijo — misma ambigüedad de hoy,
        // fuera de alcance de la separación pre-temporada/temporada regular.
        assertThat(domainGames[0].weekId).isEqualTo("2025-week-01")
        assertThat(domainGames[0].seasonType).isEqualTo(SeasonType.POSTSEASON)
    }

    private fun singleEvent(
        id: String,
        homeAbbr: String,
        awayAbbr: String,
        seasonType: Int,
        weekNumber: Int = 1
    ) = EspnEvent(
        id = id,
        date = "2025-08-07T17:00Z",
        season = EspnSeason(type = seasonType),
        week = EspnWeek(number = weekNumber),
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
