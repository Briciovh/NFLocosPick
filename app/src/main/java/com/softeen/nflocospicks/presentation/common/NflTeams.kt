package com.softeen.nflocospicks.presentation.common

data class NflTeam(val abbr: String, val name: String)

val nflTeams = listOf(
    NflTeam("ARI", "Cardinals"),  NflTeam("ATL", "Falcons"),
    NflTeam("BAL", "Ravens"),     NflTeam("BUF", "Bills"),
    NflTeam("CAR", "Panthers"),   NflTeam("CHI", "Bears"),
    NflTeam("CIN", "Bengals"),    NflTeam("CLE", "Browns"),
    NflTeam("DAL", "Cowboys"),    NflTeam("DEN", "Broncos"),
    NflTeam("DET", "Lions"),      NflTeam("GB",  "Packers"),
    NflTeam("HOU", "Texans"),     NflTeam("IND", "Colts"),
    NflTeam("JAX", "Jaguars"),    NflTeam("KC",  "Chiefs"),
    NflTeam("LAC", "Chargers"),   NflTeam("LAR", "Rams"),
    NflTeam("LV",  "Raiders"),    NflTeam("MIA", "Dolphins"),
    NflTeam("MIN", "Vikings"),    NflTeam("NE",  "Patriots"),
    NflTeam("NO",  "Saints"),     NflTeam("NYG", "Giants"),
    NflTeam("NYJ", "Jets"),       NflTeam("PHI", "Eagles"),
    NflTeam("PIT", "Steelers"),   NflTeam("SEA", "Seahawks"),
    NflTeam("SF",  "49ers"),      NflTeam("TB",  "Buccaneers"),
    NflTeam("TEN", "Titans"),     NflTeam("WSH", "Commanders")
)
