package com.example.nflocospick.presentation.proposals

// ═══════════════════════════════════════════════════════════════
//  PROPOSAL 2 — BLUE STEEL 🏈
//  Deep cobalt · Gold highlights · Scoreboard aesthetic
// ═══════════════════════════════════════════════════════════════

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Palette ─────────────────────────────────────────────────────────────────
private val B_Bg           = Color(0xFF0B2156)
private val B_Header       = Color(0xFF081840)
private val B_Surface      = Color(0xFF0F2A6B)
private val B_Card         = Color(0xFF162D72)
private val B_CardSelected = Color(0xFF1A3ABA)
private val B_Gold         = Color(0xFFFFB800)
private val B_White        = Color(0xFFFFFFFF)
private val B_Muted        = Color(0xFF7A9ABF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Proposal2(onBack: () -> Unit) {
    var picks by remember { mutableStateOf(mapOf<Int, String>()) }
    val pickedCount = picks.size
    val allPicked = pickedCount == mockWeekGames.size

    Scaffold(
        containerColor = B_Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "BLUE STEEL PICKS",
                            color = B_Gold,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "SEMANA 12 · 2025",
                            color = B_Muted,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Atrás", color = B_Gold, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = B_Header)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(B_Header)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (allPicked) B_Gold else B_Gold.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (allPicked) "⭐  CONFIRMAR PICKS  ⭐"
                        else "PICKS: $pickedCount / ${mockWeekGames.size}  —  FALTAN ${mockWeekGames.size - pickedCount}",
                        color = if (allPicked) B_Header else B_Gold,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        fontSize = 13.sp
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Week summary bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(B_Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "SEMANA 12",
                            color = B_Gold,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Elige un ganador por partido",
                            color = B_Muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$pickedCount",
                            color = B_White,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "/${mockWeekGames.size}",
                            color = B_Muted,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            items(mockWeekGames) { game ->
                BlueSteelGameCard(
                    game = game,
                    selectedTeam = picks[game.id],
                    onTeamSelected = { team ->
                        if (!game.isLocked) picks = picks + (game.id to team)
                    }
                )
            }
        }
    }
}

@Composable
private fun BlueSteelGameCard(
    game: MockGame,
    selectedTeam: String?,
    onTeamSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                game.gameTime,
                color = B_Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (game.isLocked) {
                Text("🔒 CERRADO", color = B_Muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BlueTeamCard(
                teamAbbr = game.awayTeam,
                teamCity = game.awayCity,
                label = "VISITANTE",
                isSelected = selectedTeam == game.awayTeam,
                isLocked = game.isLocked,
                modifier = Modifier.weight(1f),
                onClick = { onTeamSelected(game.awayTeam) }
            )
            BlueTeamCard(
                teamAbbr = game.homeTeam,
                teamCity = game.homeCity,
                label = "LOCAL",
                isSelected = selectedTeam == game.homeTeam,
                isLocked = game.isLocked,
                modifier = Modifier.weight(1f),
                onClick = { onTeamSelected(game.homeTeam) }
            )
        }
    }
}

@Composable
private fun BlueTeamCard(
    teamAbbr: String,
    teamCity: String,
    label: String,
    isSelected: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) B_CardSelected else B_Card
    val textColor = if (isSelected) B_White else B_Muted
    val border = if (isSelected) BorderStroke(2.dp, B_Gold) else BorderStroke(1.dp, B_Surface)

    Card(
        modifier = modifier
            .height(128.dp)
            .then(if (!isLocked) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                color = if (isSelected) B_Gold else B_Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    teamAbbr,
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    teamCity,
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                if (isSelected) "⭐ PICK" else if (!isLocked) "ELEGIR" else "—",
                color = if (isSelected) B_Gold else B_Muted.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
fun Proposal2Preview() {
    Proposal2(onBack = {})
}
