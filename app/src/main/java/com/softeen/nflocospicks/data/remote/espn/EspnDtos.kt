package com.softeen.nflocospicks.data.remote.espn

import com.google.gson.annotations.SerializedName

data class EspnScoreboardResponse(
    val events: List<EspnEvent>
)

data class EspnEvent(
    val id: String,
    val date: String,                           // ISO 8601 UTC e.g. "2025-11-24T18:00Z"
    val season: EspnSeason,                     // por-evento, no a nivel de respuesta —
    val week: EspnWeek,                         // ver comentario en EspnMapper.toDomain()
    val competitions: List<EspnCompetition>
)

data class EspnWeek(
    val number: Int
)

data class EspnSeason(
    val type: Int    // 1=pre-temporada, 2=temporada regular, 3=post-temporada
)

data class EspnCompetition(
    val competitors: List<EspnCompetitor>,
    val status: EspnStatus
)

data class EspnCompetitor(
    @SerializedName("homeAway") val homeAway: String,  // "home" | "away"
    val score: String?,
    val team: EspnTeam,
    val records: List<EspnRecord>? = null
)

data class EspnRecord(
    val name: String,    // "overall" | "Home" | "Road"
    val summary: String  // e.g. "10-3"
)

data class EspnTeam(
    val displayName: String,
    val abbreviation: String
)

data class EspnStatus(
    val type: EspnStatusType
)

data class EspnStatusType(
    val name: String,        // STATUS_SCHEDULED | STATUS_IN_PROGRESS | STATUS_FINAL
    val completed: Boolean
)
