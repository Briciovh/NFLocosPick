package com.softeen.nflocospicks.data.remote.espn

import retrofit2.http.GET

interface EspnApiService {

    /**
     * Obtiene el scoreboard de la semana NFL actual.
     * ESPN devuelve automáticamente la semana en curso sin parámetros adicionales.
     * Base URL: https://site.api.espn.com/apis/site/v2/sports/football/nfl/
     */
    @GET("scoreboard")
    suspend fun getScoreboard(): EspnScoreboardResponse
}
