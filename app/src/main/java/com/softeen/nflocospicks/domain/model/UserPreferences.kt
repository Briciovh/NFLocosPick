package com.softeen.nflocospicks.domain.model

data class UserPreferences(
    val favoriteTeamAbbr: String? = null,
    val useTestingData: Boolean = false
)
