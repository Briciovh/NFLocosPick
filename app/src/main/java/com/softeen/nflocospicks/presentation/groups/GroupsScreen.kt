package com.softeen.nflocospicks.presentation.groups

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Consume efectos de un solo disparo (navegación + feedback de puntuación)
    LaunchedEffect(Unit) {
        for (effect in viewModel.effects) {
            when (effect) {
                is GroupUiEffect.NavigateToGroupSession -> onNavigateToGroup(effect.groupId)
                GroupUiEffect.NavigateToLogin           -> onSignedOut()
                is GroupUiEffect.ScoringResult      -> {
                    val msg = if (effect.newlyScoredCount == 0)
                        context.getString(R.string.scoring_none)
                    else
                        context.getString(R.string.scoring_result, effect.newlyScoredCount)
                    snackbarHostState.showSnackbar(msg)
                }
                is GroupUiEffect.ScoringError       -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    GroupsScreenContent(
        listState               = listState,
        snackbarHostState       = snackbarHostState,
        onNavigateToCreateGroup = onNavigateToCreateGroup,
        onNavigateToJoinGroup   = onNavigateToJoinGroup,
        onNavigateToSettings    = onNavigateToSettings,
        onGroupClicked          = { viewModel.onGroupClicked(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupsScreenContent(
    listState: GroupListUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToJoinGroup: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onGroupClicked: (String) -> Unit
) {
    val appColors = LocalAppColors.current

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
                        CircularProgressIndicator(color = appColors.primary)
                    }
                }

                is GroupListUiState.Error -> {
                    Text(
                        text = listState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                is GroupListUiState.Success -> {
                    if (listState.groups.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.groups_empty),
                                color = appColors.secondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(listState.groups, key = { it.id }) { group ->
                                GroupCard(
                                    group   = group,
                                    onClick = { onGroupClicked(group.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group   : Group,
    onClick : () -> Unit
) {
    val appColors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.name,
                color = appColors.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.group_info, group.inviteCode, group.memberIds.size),
                color = appColors.secondary,
                style = MaterialTheme.typography.bodySmall
            )
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
