package com.softeen.nflocospicks.data.remote.espn

import retrofit2.http.GET
import retrofit2.http.Query

interface EspnApiService {
    /**
     * Obtiene el scoreboard de la semana NFL actual.
     * "dates" es obligatorio: sin él, ESPN decide "hoy" del lado del
     * servidor y cae al día más reciente con juegos si hoy no hay ninguno,
     * en vez de devolver la semana completa. Usar [currentNflWeekDatesParam].
     */
    @GET("scoreboard")
    suspend fun getScoreboard(@Query("dates") dates: String): EspnScoreboardResponse
}
