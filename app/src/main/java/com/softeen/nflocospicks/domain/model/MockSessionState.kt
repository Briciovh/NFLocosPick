package com.softeen.nflocospicks.domain.model

data class MockSessionState(
    val simulatedScores: Map<String, Pair<Int, Int>> = emptyMap(), // gameId → (homeScore, awayScore)
    val realUserPicks:   Map<String, String>          = emptyMap()  // gameId → pickedTeamAbbr
)
