package com.softeen.nflocospicks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softeen.nflocospicks.domain.model.Game
import com.softeen.nflocospicks.domain.model.GameStatus
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.schedule.ScheduleScreenContent
import com.softeen.nflocospicks.presentation.schedule.ScheduleUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun scheduledGame(id: String, homeAbbr: String, awayAbbr: String) = Game(
        id           = id,
        weekId       = "2025-week-01",
        homeTeam     = homeAbbr,
        awayTeam     = awayAbbr,
        homeTeamAbbr = homeAbbr,
        awayTeamAbbr = awayAbbr,
        kickoffTime  = System.currentTimeMillis() + 3_600_000L,
        homeScore    = null,
        awayScore    = null,
        status       = GameStatus.SCHEDULED
    )

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            MaterialTheme {
                ScheduleScreenContent(
                    uiState        = ScheduleUiState.Loading,
                    onNavigateBack = {},
                    onRetry        = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.LOADING_INDICATOR)
            .assertIsDisplayed()
    }

    @Test
    fun success_state_renders_one_card_per_game() {
        composeRule.setContent {
            MaterialTheme {
                ScheduleScreenContent(
                    uiState = ScheduleUiState.Success(
                        weekId = "2025-week-01",
                        games  = listOf(
                            scheduledGame("g1", "KC", "LV"),
                            scheduledGame("g2", "SF", "DAL")
                        )
                    ),
                    onNavigateBack = {},
                    onRetry        = {}
                )
            }
        }

        composeRule
            .onAllNodesWithTag(TestTags.SCHEDULE_GAME_CARD)
            .assertCountEquals(2)
    }

    @Test
    fun error_state_shows_error_message_and_retry_button() {
        composeRule.setContent {
            MaterialTheme {
                ScheduleScreenContent(
                    uiState        = ScheduleUiState.Error("No se pudo cargar"),
                    onNavigateBack = {},
                    onRetry        = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.ERROR_MESSAGE)
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(TestTags.RETRY_BUTTON)
            .assertIsDisplayed()
    }
}
