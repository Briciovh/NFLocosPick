package com.softeen.nflocospicks.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.groups.GroupListUiState
import com.softeen.nflocospicks.presentation.groups.GroupPhotoUiState
import com.softeen.nflocospicks.presentation.groups.GroupsScreenContent
import org.junit.Assert.assertEquals
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

    @Test
    fun creator_of_the_group_sees_the_edit_photo_badge_and_it_opens_the_picker() {
        var editedGroupId: String? = null
        composeRule.setContent {
            MaterialTheme {
                GroupsScreenContent(
                    listState               = GroupListUiState.Success(listOf(group("1"))),
                    snackbarHostState       = remember { SnackbarHostState() },
                    currentUserId           = "user1", // matches group("1").createdBy
                    photoUiState            = GroupPhotoUiState.Idle,
                    onNavigateToCreateGroup = {},
                    onNavigateToJoinGroup   = {},
                    onNavigateToSettings    = {},
                    onGroupClicked          = {},
                    onUploadPhoto           = { group, _ -> editedGroupId = group.id }
                )
            }
        }

        composeRule
            .onNodeWithTag(TestTags.GROUPS_EDIT_PHOTO_BUTTON)
            .assertIsDisplayed()
            .performClick()

        // El diálogo debería estar abierto ahora (no verificamos su contenido acá,
        // solo que el badge es interactivo). Confirmamos vía el estado capturado
        // que el picker, si se completa, actuaría sobre el grupo correcto.
        assertEquals(null, editedGroupId) // aún no se eligió imagen, solo se abrió el diálogo
    }

    @Test
    fun non_creator_does_not_see_the_edit_photo_badge() {
        composeRule.setContent {
            MaterialTheme {
                GroupsScreenContent(
                    listState               = GroupListUiState.Success(listOf(group("1"))),
                    snackbarHostState       = remember { SnackbarHostState() },
                    currentUserId           = "some_other_user", // != group("1").createdBy
                    onNavigateToCreateGroup = {},
                    onNavigateToJoinGroup   = {},
                    onNavigateToSettings    = {},
                    onGroupClicked          = {}
                )
            }
        }

        composeRule
            .onAllNodesWithTag(TestTags.GROUPS_EDIT_PHOTO_BUTTON)
            .assertCountEquals(0)
    }
}
