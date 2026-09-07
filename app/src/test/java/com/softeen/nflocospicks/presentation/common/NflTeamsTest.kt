package com.softeen.nflocospicks.presentation.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NflTeamsTest {

    @Test
    fun `nflTeams has all 32 franchises with unique abbreviations`() {
        assertThat(nflTeams).hasSize(32)
        assertThat(nflTeams.map { it.abbr }.toSet()).hasSize(32)
    }

    @Test
    fun `no abbreviation or name is blank`() {
        nflTeams.forEach { team ->
            assertThat(team.abbr).isNotEmpty()
            assertThat(team.name).isNotEmpty()
        }
    }

    @Test
    fun `nflTeamNameByAbbr maps every abbreviation to its name`() {
        assertThat(nflTeamNameByAbbr).hasSize(32)
        nflTeams.forEach { team ->
            assertThat(nflTeamNameByAbbr[team.abbr]).isEqualTo(team.name)
        }
    }

    @Test
    fun `nflTeamNameByAbbr returns null for an unknown abbreviation`() {
        assertThat(nflTeamNameByAbbr["XXX"]).isNull()
    }

    @Test
    fun `nflTeamColorMap covers exactly the same abbreviations as nflTeams`() {
        assertThat(nflTeamColorMap.keys).containsExactlyElementsIn(nflTeams.map { it.abbr })
    }
}
