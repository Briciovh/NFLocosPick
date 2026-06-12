package com.softeen.nflocospicks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softeen.nflocospicks.presentation.auth.AuthUiState
import com.softeen.nflocospicks.presentation.auth.LoginScreenContent
import com.softeen.nflocospicks.presentation.common.TestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sign_in_button_is_displayed_and_enabled_in_idle_state() {
        composeRule.setContent {
            MaterialTheme {
                LoginScreenContent(
                    state    = AuthUiState.Idle,
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
    fun loading_state_shows_progress_indicator_and_disables_button() {
        composeRule.setContent {
            MaterialTheme {
                LoginScreenContent(
                    state    = AuthUiState.Loading,
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

    @Test
    fun clicking_sign_in_button_invokes_callback() {
        var clicked = false

        composeRule.setContent {
            MaterialTheme {
                LoginScreenContent(
                    state    = AuthUiState.Idle,
                    onSignIn = { clicked = true }
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.LOGIN_SIGN_IN_BUTTON)
            .performClick()

        assertTrue("onSignIn must be called after button tap", clicked)
    }
}
