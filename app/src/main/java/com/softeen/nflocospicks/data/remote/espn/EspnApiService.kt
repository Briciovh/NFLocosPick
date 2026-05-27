package com.softeen.nflocospicks.data.remote.espn

import retrofit2.http.GET

interface EspnApiService {
    /**
     * Obtiene el scoreboard de la semana NFL actual.
     * Sin parámetros = semana en curso.
     */
    @GET("scoreboard")
    suspend fun getScoreboard(): EspnScoreboardResponse
}
