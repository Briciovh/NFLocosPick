package com.softeen.nflocospicks.presentation.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softeen.nflocospicks.domain.model.LeaderboardEntry
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeLeaderboard
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

private val SilverColor = Color(0xFFB0BEC5)
private val BronzeColor = Color(0xFFBF8970)

@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (groupId: String) -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId  = viewModel.currentUserId
    val groupId        = viewModel.groupId

    LeaderboardScreenContent(
        uiState             = uiState,
        currentUserId       = currentUserId,
        groupId             = groupId,
        onNavigateBack      = onNavigateBack,
        onNavigateToHistory = onNavigateToHistory
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaderboardScreenContent(
    uiState: LeaderboardUiState,
    currentUserId: String?,
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (groupId: String) -> Unit
) {
    val appColors = LocalAppColors.current

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.leaderboard_title),
                        color = appColors.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = appColors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is LeaderboardUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = appColors.primary)
                }
            }

            is LeaderboardUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            is LeaderboardUiState.Success -> {
                if (uiState.entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.leaderboard_empty),
                            color = appColors.secondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                } else {
                    val expanded = remember { mutableStateMapOf<String, Boolean>() }

                    LazyColumn(
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp)
                    ) {
                        items(uiState.entries, key = { it.userId }) { entry ->
                            MemberCard(
                                entry = entry,
                                isExpanded = expanded[entry.userId] == true,
                                onToggle = {
                                    expanded[entry.userId] = !(expanded[entry.userId] ?: false)
                                },
                                isOwnCard = entry.userId == currentUserId,
                                onViewHistory = { onNavigateToHistory(groupId) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(
    entry: LeaderboardEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isOwnCard: Boolean = false,
    onViewHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RankBadge(rank = entry.rank)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        color = appColors.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.points_format, entry.totalPoints),
                        color = appColors.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appColors.secondary
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    WeeklyBreakdown(weeklyBreakdown = entry.weeklyBreakdown)
                    if (isOwnCard) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onViewHistory,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.leaderboard_view_history),
                                color = appColors.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val appColors = LocalAppColors.current
    val (bgColor, textColor) = when (rank) {
        1    -> appColors.primary to appColors.onPrimary
        2    -> SilverColor to Color.White
        3    -> BronzeColor to Color.White
        else -> appColors.header to appColors.secondary
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "#$rank",
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun WeeklyBreakdown(weeklyBreakdown: Map<String, Int>) {
    val appColors = LocalAppColors.current
    if (weeklyBreakdown.isEmpty()) return

    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = appColors.secondary.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.leaderboard_weekly_breakdown),
            color = appColors.secondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        weeklyBreakdown.entries
            .sortedBy { it.key }
            .forEach { (weekId, points) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = weekId,
                        color = appColors.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.points_format, points),
                        color = appColors.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LeaderboardScreenSuccessPreview() {
    PreviewWrapper {
        LeaderboardScreenContent(
            uiState             = LeaderboardUiState.Success(fakeLeaderboard),
            currentUserId       = "user_1",
            groupId             = "group_1",
            onNavigateBack      = {},
            onNavigateToHistory = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun LeaderboardScreenLoadingPreview() {
    PreviewWrapper {
        LeaderboardScreenContent(
            uiState             = LeaderboardUiState.Loading,
            currentUserId       = "user_1",
            groupId             = "group_1",
            onNavigateBack      = {},
            onNavigateToHistory = {}
        )
    }
}
