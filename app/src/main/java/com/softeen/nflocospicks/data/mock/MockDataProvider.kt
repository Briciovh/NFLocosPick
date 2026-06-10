package com.softeen.nflocospicks.data.mock

import com.softeen.nflocospicks.domain.model.BoardMessage
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

    // Récords de prueba para semana 13 de temporada regular (W-L).
    private val mockRecords = mapOf(
        "KC"  to "10-2", "DEN" to "5-7",
        "BUF" to "9-3",  "NE"  to "3-9",
        "MIA" to "6-6",  "NYJ" to "4-8",
        "BAL" to "9-3",  "CLE" to "3-9",
        "PIT" to "7-5",  "CIN" to "6-6",
        "HOU" to "7-5",  "IND" to "5-7",
        "JAX" to "4-8",  "TEN" to "3-9",
        "LV"  to "3-9",  "LAC" to "8-4",
        "PHI" to "10-2", "DAL" to "6-6",
        "NYG" to "2-10", "WSH" to "7-5",
        "GB"  to "8-4",  "CHI" to "4-8",
        "DET" to "11-1", "MIN" to "9-3",
        "TB"  to "7-5",  "ATL" to "6-6",
        "NO"  to "4-8",  "CAR" to "2-10",
        "LAR" to "7-5",  "SEA" to "7-5",
        "SF"  to "6-6",  "ARI" to "5-7",
    )

    // Juegos siempre en el futuro mientras no se simule (2 días + 1h por juego).
    val MOCK_GAMES: List<Game> get() {
        val base = System.currentTimeMillis() + 2 * 24 * 3_600_000L
        return matchups.mapIndexed { index, (home, away) ->
            Game(
                id             = "mock_game_${index + 1}",
                weekId         = MOCK_WEEK_ID,
                homeTeam       = home.second,
                awayTeam       = away.second,
                homeTeamAbbr   = home.first,
                awayTeamAbbr   = away.first,
                kickoffTime    = base + index * 3_600_000L,
                homeScore      = null,
                awayScore      = null,
                status         = GameStatus.SCHEDULED,
                homeTeamRecord = mockRecords[home.first],
                awayTeamRecord = mockRecords[away.first],
            )
        }
    }

    // Aplica marcadores simulados a la lista de juegos, marcándolos como FINAL.
    fun applyScores(games: List<Game>, scores: Map<String, Pair<Int, Int>>): List<Game> =
        games.map { game ->
            val score = scores[game.id] ?: return@map game
            game.copy(homeScore = score.first, awayScore = score.second, status = GameStatus.FINAL)
        }

    // Mensajes de prueba para el tablero del grupo mock.
    // mock_user_1 es el admin (MOCK_GROUP.createdBy), así que su primer mensaje es un anuncio.
    val MOCK_BOARD_MESSAGES: List<BoardMessage> get() {
        val now = System.currentTimeMillis()
        return listOf(
            BoardMessage(
                id             = "mock_msg_1",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_1",
                senderName     = "Alex Morgan",
                senderPhotoUrl = null,
                content        = "¡Bienvenidos a la semana 13! Recuerden hacer sus picks antes del jueves. Suerte a todos 🏈",
                timestamp      = now - 6 * 3_600_000L,
                isAnnouncement = true
            ),
            BoardMessage(
                id             = "mock_msg_2",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_3",
                senderName     = "Sam Rivera",
                senderPhotoUrl = null,
                content        = "Gracias Alex! Ya mandé los míos. KC toda la vida 🔥",
                timestamp      = now - 5 * 3_600_000L
            ),
            BoardMessage(
                id             = "mock_msg_3",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_2",
                senderName     = "Jordan Lee",
                senderPhotoUrl = null,
                content        = "Yo sigo con los Eagles, no me arrepiento de nada jaja",
                timestamp      = now - 4 * 3_600_000L
            ),
            BoardMessage(
                id             = "mock_msg_4",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_5",
                senderName     = "Taylor Kim",
                senderPhotoUrl = null,
                content        = "¿Alguien más va con Detroit esta semana?",
                timestamp      = now - 2 * 3_600_000L,
                editedAt       = now - 1 * 3_600_000L
            ),
            BoardMessage(
                id             = "mock_msg_5",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_7",
                senderName     = "Jamie Chen",
                senderPhotoUrl = null,
                content        = "Yo Taylor! Lions están imparables esta temporada 🦁",
                timestamp      = now - 45 * 60_000L
            ),
            BoardMessage(
                id             = "mock_msg_6",
                groupId        = MOCK_GROUP_ID,
                senderId       = "mock_user_1",
                senderName     = "Alex Morgan",
                senderPhotoUrl = null,
                content        = "Los picks cierran el jueves a las 8pm. ¡No se confíen!",
                timestamp      = now - 10 * 60_000L
            ),
        )
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
