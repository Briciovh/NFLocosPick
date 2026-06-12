package com.softeen.nflocospicks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.groups.GroupListUiState
import com.softeen.nflocospicks.presentation.groups.GroupsScreenContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun group(id: String) = Group(
        id         = id,
        name       = "Grupo $id",
        inviteCode = "CODE$id",
        createdBy  = "user1",
        memberIds  = listOf("user1")
    )

    @Test
    fun success_with_groups_shows_one_card_per_group() {
        composeRule.setContent {
            MaterialTheme {
                GroupsScreenContent(
                    listState               = GroupListUiState.Success(listOf(group("1"), group("2"))),
                    snackbarHostState       = remember { SnackbarHostState() },
                    onNavigateToCreateGroup = {},
                    onNavigateToJoinGroup   = {},
                    onNavigateToSettings    = {},
                    onGroupClicked          = {}
                )
            }
        }

        composeRule
            .onAllNodesWithTag(TestTags.GROUPS_GROUP_CARD)
            .assertCountEquals(2)
    }

    @Test
    fun success_with_empty_list_shows_empty_state_text() {
        composeRule.setContent {
            MaterialTheme {
                GroupsScreenContent(
                    listState               = GroupListUiState.Success(emptyList()),
                    snackbarHostState       = remember { SnackbarHostState() },
                    onNavigateToCreateGroup = {},
                    onNavigateToJoinGroup   = {},
                    onNavigateToSettings    = {},
                    onGroupClicked          = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.GROUPS_EMPTY_STATE)
            .assertIsDisplayed()
    }
}
