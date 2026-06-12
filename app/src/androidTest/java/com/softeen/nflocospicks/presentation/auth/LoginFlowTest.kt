package com.softeen.nflocospicks.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme
import org.junit.Rule
import org.junit.Test

class LoginFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun login_screen_idle_state_shows_sign_in_button() {
        composeRule.setContent {
            NFLocosPickTheme {
                LoginScreenContent(
                    state = AuthUiState.Idle,
                    onSignIn = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.LOGIN_SIGN_IN_BUTTON)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun login_screen_loading_state_shows_progress_indicator_and_disables_button() {
        composeRule.setContent {
            NFLocosPickTheme {
                LoginScreenContent(
                    state = AuthUiState.Loading,
                    onSignIn = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.LOADING_INDICATOR)
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(TestTags.LOGIN_SIGN_IN_BUTTON)
            .assertIsNotEnabled()
    }
}
