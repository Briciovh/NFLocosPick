package com.softeen.nflocospicks.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GamePickResult
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserPreferences
import com.softeen.nflocospicks.domain.model.WeekHistoryEntry
import com.softeen.nflocospicks.presentation.picks.GamePickItem
import com.softeen.nflocospicks.presentation.theme.AppColors
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme

// ── Fake domain objects ───────────────────────────────────────────────────────

internal val fakeGame = Game(
    id           = "game_1",
    weekId       = "2025-week-12",
    homeTeam     = "Kansas City Chiefs",
    awayTeam     = "Dallas Cowboys",
    homeTeamAbbr = "KC",
    awayTeamAbbr = "DAL",
    kickoffTime  = System.currentTimeMillis() + 7_200_000L,
    homeScore    = null,
    awayScore    = null,
    status       = GameStatus.SCHEDULED
)

internal val fakeGameLive = Game(
    id           = "game_2",
    weekId       = "2025-week-12",
    homeTeam     = "Green Bay Packers",
    awayTeam     = "Chicago Bears",
    homeTeamAbbr = "GB",
    awayTeamAbbr = "CHI",
    kickoffTime  = System.currentTimeMillis() - 3_600_000L,
    homeScore    = 17,
    awayScore    = 14,
    status       = GameStatus.IN_PROGRESS
)

internal val fakeUser = User(
    uid         = "user_1",
    displayName = "Bricio Velázquez",
    email       = "briciovh@gmail.com",
    photoUrl    = null
)

internal val fakeGroup = Group(
    id        = "group_1",
    name      = "Los Locos del NFL",
    inviteCode = "LCO123",
    createdBy = "user_1",
    memberIds = listOf("user_1", "user_2", "user_3")
)

internal val fakeLeaderboard = listOf(
    LeaderboardEntry(
        userId = "user_1", displayName = "Bricio", photoUrl = null,
        totalPoints = 42, weeklyBreakdown = mapOf("2025-week-10" to 7, "2025-week-11" to 9),
        rank = 1
    ),
    LeaderboardEntry(
        userId = "user_2", displayName = "Juan", photoUrl = null,
        totalPoints = 36, weeklyBreakdown = mapOf("2025-week-10" to 6, "2025-week-11" to 7),
        rank = 2
    ),
    LeaderboardEntry(
        userId = "user_3", displayName = "María", photoUrl = null,
        totalPoints = 28, weeklyBreakdown = mapOf("2025-week-10" to 5, "2025-week-11" to 4),
        rank = 3
    ),
)

internal val fakePickItem = GamePickItem(
    game       = fakeGame,
    pickedTeam = "KC",
    isLocked   = false
)

internal val fakePickItemLocked = GamePickItem(
    game       = fakeGameLive,
    pickedTeam = null,
    isLocked   = true
)

internal val fakeHistory = listOf(
    WeekHistoryEntry(
        weekId     = "2025-week-12",
        weekPoints = 7,
        picks      = listOf(
            GamePickResult(
                game           = fakeGame,
                pickedTeam     = "KC",
                isCorrect      = true,
                scoredAt       = System.currentTimeMillis(),
                winnerTeamAbbr = "KC"
            )
        )
    ),
    WeekHistoryEntry(
        weekId     = "2025-week-11",
        weekPoints = 5,
        picks      = listOf(
            GamePickResult(
                game           = fakeGameLive,
                pickedTeam     = "CHI",
                isCorrect      = false,
                scoredAt       = System.currentTimeMillis(),
                winnerTeamAbbr = "GB"
            )
        )
    )
)

internal val fakePrefs = UserPreferences(favoriteTeamAbbr = "KC")

// ── Preview wrapper ───────────────────────────────────────────────────────────

@Composable
internal fun PreviewWrapper(content: @Composable () -> Unit) {
    val defaultColors = LocalAppColors.current
    CompositionLocalProvider(LocalAppColors provides defaultColors) {
        NFLocosPickTheme(appColors = defaultColors) {
            content()
        }
    }
}
