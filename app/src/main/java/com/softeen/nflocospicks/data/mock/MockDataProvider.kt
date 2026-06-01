package com.softeen.nflocospicks.data.mock

import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.User

object MockDataProvider {

    const val MOCK_GROUP_ID   = "mock_testing_group"
    const val MOCK_WEEK_ID    = "mock-week-01"
    private const val MOCK_USER_COUNT = 9

    // 16 partidos usando los 32 equipos reales de la NFL
    private val matchups = listOf(
        ("KC"  to "Kansas City Chiefs")     vs ("DEN" to "Denver Broncos"),
        ("BUF" to "Buffalo Bills")          vs ("NE"  to "New England Patriots"),
        ("MIA" to "Miami Dolphins")         vs ("NYJ" to "New York Jets"),
        ("BAL" to "Baltimore Ravens")       vs ("CLE" to "Cleveland Browns"),
        ("PIT" to "Pittsburgh Steelers")    vs ("CIN" to "Cincinnati Bengals"),
        ("HOU" to "Houston Texans")         vs ("IND" to "Indianapolis Colts"),
        ("JAX" to "Jacksonville Jaguars")   vs ("TEN" to "Tennessee Titans"),
        ("LV"  to "Las Vegas Raiders")      vs ("LAC" to "Los Angeles Chargers"),
        ("PHI" to "Philadelphia Eagles")    vs ("DAL" to "Dallas Cowboys"),
        ("NYG" to "New York Giants")        vs ("WSH" to "Washington Commanders"),
        ("GB"  to "Green Bay Packers")      vs ("CHI" to "Chicago Bears"),
        ("DET" to "Detroit Lions")          vs ("MIN" to "Minnesota Vikings"),
        ("TB"  to "Tampa Bay Buccaneers")   vs ("ATL" to "Atlanta Falcons"),
        ("NO"  to "New Orleans Saints")     vs ("CAR" to "Carolina Panthers"),
        ("LAR" to "Los Angeles Rams")       vs ("SEA" to "Seattle Seahawks"),
        ("SF"  to "San Francisco 49ers")    vs ("ARI" to "Arizona Cardinals"),
    )

    // memberIds sólo incluye mock users; GroupViewModel agrega el UID del usuario real.
    val MOCK_GROUP: Group = Group(
        id         = MOCK_GROUP_ID,
        name       = "Grupo de Testing",
        inviteCode = "TEST01",
        createdBy  = "mock_user_1",
        memberIds  = (1..MOCK_USER_COUNT).map { "mock_user_$it" }
    )

    val MOCK_USERS: List<User> = listOf(
        User(uid = "mock_user_1", displayName = "Alex Morgan",   email = "mock1@test.com", photoUrl = null),
        User(uid = "mock_user_2", displayName = "Jordan Lee",    email = "mock2@test.com", photoUrl = null),
        User(uid = "mock_user_3", displayName = "Sam Rivera",    email = "mock3@test.com", photoUrl = null),
        User(uid = "mock_user_4", displayName = "Casey Brooks",  email = "mock4@test.com", photoUrl = null),
        User(uid = "mock_user_5", displayName = "Taylor Kim",    email = "mock5@test.com", photoUrl = null),
        User(uid = "mock_user_6", displayName = "Morgan Davis",  email = "mock6@test.com", photoUrl = null),
        User(uid = "mock_user_7", displayName = "Jamie Chen",    email = "mock7@test.com", photoUrl = null),
        User(uid = "mock_user_8", displayName = "Riley Torres",  email = "mock8@test.com", photoUrl = null),
        User(uid = "mock_user_9", displayName = "Quinn Reyes",   email = "mock9@test.com", photoUrl = null),
    )

    // Juegos siempre en el futuro mientras no se simule (2 días + 1h por juego).
    val MOCK_GAMES: List<Game> get() {
        val base = System.currentTimeMillis() + 2 * 24 * 3_600_000L
        return matchups.mapIndexed { index, (home, away) ->
            Game(
                id           = "mock_game_${index + 1}",
                weekId       = MOCK_WEEK_ID,
                homeTeam     = home.second,
                awayTeam     = away.second,
                homeTeamAbbr = home.first,
                awayTeamAbbr = away.first,
                kickoffTime  = base + index * 3_600_000L,
                homeScore    = null,
                awayScore    = null,
                status       = GameStatus.SCHEDULED
            )
        }
    }

    // Aplica marcadores simulados a la lista de juegos, marcándolos como FINAL.
    fun applyScores(games: List<Game>, scores: Map<String, Pair<Int, Int>>): List<Game> =
        games.map { game ->
            val score = scores[game.id] ?: return@map game
            game.copy(homeScore = score.first, awayScore = score.second, status = GameStatus.FINAL)
        }

    // Picks determinísticos para mock users: par → home, impar → away.
    fun getPicksForMockUser(userIndex: Int, games: List<Game>): Map<String, String> =
        games.mapIndexed { gameIndex, game ->
            val abbr = if ((userIndex + gameIndex) % 2 == 0) game.homeTeamAbbr else game.awayTeamAbbr
            game.id to abbr
        }.toMap()
}

// Alias de infix para que los matchups sean legibles en la definición de arriba.
private infix fun Pair<String, String>.vs(other: Pair<String, String>) = Pair(this, other)
