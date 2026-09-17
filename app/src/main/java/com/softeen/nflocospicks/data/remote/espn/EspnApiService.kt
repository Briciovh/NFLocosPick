package com.softeen.nflocospicks.data.remote.espn

import retrofit2.http.GET
import retrofit2.http.Query

interface EspnApiService {
    /**
     * Scoreboard de una fecha específica ("dates" es una sola fecha YYYYMMDD —
     * ESPN rechaza con 400 cualquier valor de rango, ver
     * docs/plans/fix-espn-current-week-400.md). Se usa para dos cosas: traer los
     * juegos de un día puntual, y leer `leagues[0].calendar` de la respuesta
     * (presente incluso sin eventos ese día) para resolver la semana actual —
     * ver [resolveCurrentSeasonWeek].
     */
    @GET("scoreboard")
    suspend fun getScoreboard(@Query("dates") dates: String): EspnScoreboardResponse

    /**
     * Scoreboard de una semana específica. seasonType: 1=pre, 2=regular, 3=post.
     * Pretemporada: week=1 es el Hall of Fame Game (1 evento), 2-4 son las
     * tres semanas reales de pretemporada. Regular: 1-18. Postemporada:
     * 1,2,3,5 (4 es el bye de Pro Bowl, devuelve 0 eventos).
     * No se combina con "dates" — acá se pide una semana explícita, no "la
     * actual".
     */
    @GET("scoreboard")
    suspend fun getScoreboardForWeek(
        @Query("seasontype") seasonType: Int,
        @Query("week") week: Int
    ): EspnScoreboardResponse
}
