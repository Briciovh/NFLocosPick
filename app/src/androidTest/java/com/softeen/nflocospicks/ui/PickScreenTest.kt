package com.softeen.nflocospicks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.picks.GamePickItem
import com.softeen.nflocospicks.presentation.picks.PickScreenContent
import com.softeen.nflocospicks.presentation.picks.PickUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PickScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun game(id: String, homeAbbr: String, awayAbbr: String, locked: Boolean) = Game(
        id           = id,
        weekId       = "2025-week-01",
        homeTeam     = homeAbbr,
        awayTeam     = awayAbbr,
        homeTeamAbbr = homeAbbr,
        awayTeamAbbr = awayAbbr,
        kickoffTime  = if (locked) 0L else System.currentTimeMillis() + 3_600_000L,
        homeScore    = null,
        awayScore    = null,
        status       = if (locked) GameStatus.IN_PROGRESS else GameStatus.SCHEDULED
    )

    private fun pickItem(game: Game) = GamePickItem(
        game       = game,
        pickedTeam = null,
        isLocked   = game.kickoffTime < System.currentTimeMillis()
    )

    @Test
    fun success_state_renders_one_card_per_item() {
        val game1 = game("g1", "KC", "LV", locked = false)
        val game2 = game("g2", "SF", "DAL", locked = false)

        composeRule.setContent {
            MaterialTheme {
                PickScreenContent(
                    uiState        = PickUiState.Success(
                        weekId = "2025-week-01",
                        items  = listOf(pickItem(game1), pickItem(game2))
                    ),
                    errorMessage   = null,
                    onNavigateBack = {},
                    onRetry        = {},
                    onSync         = {},
                    onPick         = { _, _, _ -> },
                    onErrorShown   = {}
                )
            }
        }

        composeRule
            .onAllNodesWithTag(TestTags.PICK_GAME_CARD)
            .assertCountEquals(2)
    }

    @Test
    fun locked_game_team_buttons_are_disabled() {
        val lockedGame = game("g1", "KC", "LV", locked = true)

        composeRule.setContent {
            MaterialTheme {
                PickScreenContent(
                    uiState        = PickUiState.Success(
                        weekId = "2025-week-01",
                        items  = listOf(GamePickItem(lockedGame, pickedTeam = null, isLocked = true))
                    ),
                    errorMessage   = null,
                    onNavigateBack = {},
                    onRetry        = {},
                    onSync         = {},
                    onPick         = { _, _, _ -> },
                    onErrorShown   = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("${TestTags.PICK_TEAM_BUTTON}_KC")
            .assertIsNotEnabled()

        composeRule
            .onNodeWithTag("${TestTags.PICK_TEAM_BUTTON}_LV")
            .assertIsNotEnabled()
    }

    @Test
    fun tapping_team_button_invokes_onPick_with_correct_abbr() {
        val g = game("g1", "KC", "LV", locked = false)
        var pickedAbbr: String? = null

        composeRule.setContent {
            MaterialTheme {
                PickScreenContent(
                    uiState        = PickUiState.Success(
                        weekId = "2025-week-01",
                        items  = listOf(pickItem(g))
                    ),
                    errorMessage   = null,
                    onNavigateBack = {},
                    onRetry        = {},
                    onSync         = {},
                    onPick         = { _, abbr, _ -> pickedAbbr = abbr },
                    onErrorShown   = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("${TestTags.PICK_TEAM_BUTTON}_KC")
            .performClick()

        assertTrue("onPick must be called with 'KC'", pickedAbbr == "KC")
    }
}
