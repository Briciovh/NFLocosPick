package com.softeen.nflocospicks.domain.model

data class UserPreferences(
    val favoriteTeamAbbr:     String?  = null,
    val useTestingData:       Boolean  = false,
    val simulateGamesStarted: Boolean  = false,
    val languageTag:          String?  = null
)
