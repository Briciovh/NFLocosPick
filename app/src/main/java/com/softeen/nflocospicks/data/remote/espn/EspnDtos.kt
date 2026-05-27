package com.softeen.nflocospicks.data.remote.espn

import com.google.gson.annotations.SerializedName

// ── Raíz del scoreboard ───────────────────────────────────────────────────────

data class EspnScoreboardResponse(
    val week: EspnWeek?,
    val events: List<EspnEvent>?
)

data class EspnWeek(
    val number: Int?
)

// ── Evento (partido) ──────────────────────────────────────────────────────────

data class EspnEvent(
    val id: String?,
    val date: String?,              // ISO 8601 UTC, e.g. "2025-09-07T17:00Z"
    val competitions: List<EspnCompetition>?
)

// ── Competencia (datos de un evento: equipos + status) ────────────────────────

data class EspnCompetition(
    val competitors: List<EspnCompetitor>?,
    val status: EspnCompetitionStatus?
)

data class EspnCompetitionStatus(
    val type: EspnStatusType?
)

data class EspnStatusType(
    val name: String?,          // "STATUS_SCHEDULED" | "STATUS_IN_PROGRESS" | "STATUS_FINAL"
    val completed: Boolean?
)

// ── Competidor (un equipo dentro del partido) ─────────────────────────────────

data class EspnCompetitor(
    val homeAway: String?,      // "home" | "away"
    val score: String?,         // e.g. "24" — puede ser null o vacío antes de empezar
    val team: EspnTeam?
)

data class EspnTeam(
    val displayName: String?,
    val abbreviation: String?
)
