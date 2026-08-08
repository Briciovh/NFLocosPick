package com.softeen.nflocospicks.presentation.groups

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.softeen.nflocospicks.presentation.common.GroupAvatar
import com.softeen.nflocospicks.presentation.common.TestTags
import com.softeen.nflocospicks.presentation.common.responsiveCardWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeGroup
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun GroupsScreen(
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToGroup: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val listState by viewModel.groupListState.collectAsStateWithLifecycle()
    val photoUiState by viewModel.photoUiState.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    val snackbarHostState = remember { SnackbarHostState() }
    val scoringNoneMsg = stringResource(R.string.scoring_none)
    val scoringResultPattern = stringResource(R.string.scoring_result)

    // Consume efectos de un solo disparo (navegación + feedback de puntuación)
    LaunchedEffect(Unit) {
        for (effect in viewModel.effects) {
            when (effect) {
                is GroupUiEffect.NavigateToGroupSession -> onNavigateToGroup(effect.groupId)
                GroupUiEffect.NavigateToLogin           -> onSignedOut()
                is GroupUiEffect.ScoringResult      -> {
                    val msg = if (effect.newlyScoredCount == 0)
                        scoringNoneMsg
                    else
                        String.format(scoringResultPattern, effect.newlyScoredCount)
                    snackbarHostState.showSnackbar(msg)
                }
                is GroupUiEffect.ScoringError       -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    GroupsScreenContent(
        listState               = listState,
        snackbarHostState       = snackbarHostState,
        currentUserId           = currentUserId,
        photoUiState            = photoUiState,
        onNavigateToCreateGroup = onNavigateToCreateGroup,
        onNavigateToJoinGroup   = onNavigateToJoinGroup,
        onNavigateToSettings    = onNavigateToSettings,
        onGroupClicked          = { viewModel.onGroupClicked(it) },
        onUploadPhoto           = { group, uri -> currentUserId?.let { viewModel.uploadGroupPhoto(group, it, uri) } },
        onSetIcon               = { group, iconId -> currentUserId?.let { viewModel.setGroupIcon(group, it, iconId) } },
        onDismissPhotoPicker    = { viewModel.resetPhotoUiState() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupsScreenContent(
    listState: GroupListUiState,
    snackbarHostState: SnackbarHostState,
    currentUserId: String? = null,
    photoUiState: GroupPhotoUiState = GroupPhotoUiState.Idle,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onGroupClicked: (String) -> Unit,
    onUploadPhoto: (Group, Uri) -> Unit = { _, _ -> },
    onSetIcon: (Group, String) -> Unit = { _, _ -> },
    onDismissPhotoPicker: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    var editingGroup by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "NFL PICKS",
                        color = appColors.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = appColors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        },
        snackbarHost   = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = appColors.surfaceVariant,
                    contentColor   = appColors.onSurface,
                    actionColor    = appColors.primary
                )
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // FAB secundario: unirse con código
                FloatingActionButton(
                    onClick = onNavigateToJoinGroup,
                    containerColor = appColors.surface
                ) {
                    Text("🔗", style = MaterialTheme.typography.titleMedium)
                }
                // FAB principal: crear grupo
                FloatingActionButton(
                    onClick = onNavigateToCreateGroup,
                    containerColor = appColors.primary
                ) {
                    Text("+", color = appColors.onPrimary, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            when (listState) {
                is GroupListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color    = appColors.primary,
                            modifier = Modifier.testTag(TestTags.LOADING_INDICATOR)
                        )
                    }
                }

                is GroupListUiState.Error -> {
                    Text(
                        text     = listState.message,
                        color    = MaterialTheme.colorScheme.error,
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp).testTag(TestTags.ERROR_MESSAGE)
                    )
                }

                is GroupListUiState.Success -> {
                    if (listState.groups.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text      = stringResource(R.string.groups_empty),
                                color     = appColors.secondary,
                                style     = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.testTag(TestTags.GROUPS_EMPTY_STATE)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            items(listState.groups, key = { it.id }) { group ->
                                GroupCard(
                                    group      = group,
                                    canEdit    = currentUserId != null && group.createdBy == currentUserId,
                                    onClick    = { onGroupClicked(group.id) },
                                    onEditPhoto = { editingGroup = group }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingGroup?.let { group ->
        GroupImagePickerDialog(
            photoUiState = photoUiState,
            onPickPhoto  = { uri -> onUploadPhoto(group, uri) },
            onPickIcon   = { iconId -> onSetIcon(group, iconId) },
            onDismiss    = {
                editingGroup = null
                onDismissPhotoPicker()
            }
        )
    }
}

@Composable
private fun GroupCard(
    group       : Group,
    canEdit     : Boolean = false,
    onClick     : () -> Unit,
    onEditPhoto : () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .responsiveCardWidth()
            .testTag(TestTags.GROUPS_GROUP_CARD)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                GroupAvatar(
                    photoUrl = group.photoUrl,
                    iconId   = group.iconId,
                    name     = group.name,
                    size     = 96.dp
                )
                if (canEdit) {
                    Box(
                        modifier          = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(appColors.primary)
                            .clickable(onClick = onEditPhoto)
                            .testTag(TestTags.GROUPS_EDIT_PHOTO_BUTTON),
                        contentAlignment  = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_edit_group_photo),
                            tint               = appColors.onPrimary,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = group.name,
                    color = appColors.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.group_info, group.inviteCode, group.memberIds.size),
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun GroupsScreenSuccessPreview() {
    PreviewWrapper {
        GroupsScreenContent(
            listState               = GroupListUiState.Success(listOf(fakeGroup)),
            snackbarHostState       = remember { SnackbarHostState() },
            onNavigateToCreateGroup = {},
            onNavigateToJoinGroup   = {},
            onNavigateToSettings    = {},
            onGroupClicked          = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun GroupsScreenEmptyPreview() {
    PreviewWrapper {
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
