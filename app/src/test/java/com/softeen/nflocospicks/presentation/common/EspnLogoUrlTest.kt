package com.softeen.nflocospicks.presentation.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EspnLogoUrlTest {

    @Test
    fun `builds the ESPN 500px CDN url with a lowercased abbreviation`() {
        assertThat(espnTeamLogoUrl("KC"))
            .isEqualTo("https://a.espncdn.com/i/teamlogos/nfl/500/kc.png")
    }

    @Test
    fun `already-lowercase input is unchanged`() {
        assertThat(espnTeamLogoUrl("sf"))
            .isEqualTo("https://a.espncdn.com/i/teamlogos/nfl/500/sf.png")
    }

    @Test
    fun `every team in nflTeams produces the exact expected url`() {
        nflTeams.forEach { team ->
            assertThat(espnTeamLogoUrl(team.abbr))
                .isEqualTo("https://a.espncdn.com/i/teamlogos/nfl/500/${team.abbr.lowercase()}.png")
        }
    }
}
