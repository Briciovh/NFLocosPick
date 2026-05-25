package com.example.nflocospick.presentation.proposals

// ═══════════════════════════════════════════════════════════════
//  PROPOSAL 1 — INFERNO 🔥
//  Dark charcoal · Fire-orange accents · Ember-red selections
// ═══════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Palette ─────────────────────────────────────────────────────────────────
private val I_Bg        = Color(0xFF0D0D0D)
private val I_Surface   = Color(0xFF1C1C1C)
private val I_Bar       = Color(0xFF1A1A1A)
private val I_Orange    = Color(0xFFFF6B00)
private val I_Gold      = Color(0xFFFFB800)
private val I_Red       = Color(0xFFCC2222)
private val I_Muted     = Color(0xFF888888)
private val I_Divider   = Color(0xFF2E2E2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Proposal1(onBack: () -> Unit) {
    var picks by remember { mutableStateOf(mapOf<Int, String>()) }
    val pickedCount = picks.size

    Scaffold(
        containerColor = I_Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🔥 INFERNO PICKS",
                            color = I_Orange,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "SEMANA 12 · TEMPORADA 2025",
                            color = I_Muted,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Atrás", color = I_Orange, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = I_Bar)
            )
        },
        bottomBar = {
            Surface(color = I_Bar) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = I_Orange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "🔥  CONFIRMAR PICKS  ($pickedCount / ${mockWeekGames.size})",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            )
        ) {
            item {
                // Info strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(I_Surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Cierra en: 2d 14h 32m",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Elige un equipo por partido",
                            color = I_Muted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            items(mockWeekGames) { game ->
                InfernoGameCard(
                    game = game,
                    selectedTeam = picks[game.id],
                    onTeamSelected = { team ->
                        if (!game.isLocked) picks = picks + (game.id to team)
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun InfernoGameCard(
    game: MockGame,
    selectedTeam: String?,
    onTeamSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = I_Surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRect(color = I_Orange, size = Size(4.dp.toPx(), size.height))
                }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game.gameTime,
                    color = I_Orange,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (game.isLocked) {
                    Spacer(Modifier.width(8.dp))
                    Text("🔒", fontSize = 12.sp)
                    Text(
                        " CERRADO",
                        color = I_Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            InfernoTeamRow(
                teamAbbr = game.awayTeam,
                teamCity = "${game.awayCity}  ·  Visitante",
                isSelected = selectedTeam == game.awayTeam,
                isLocked = game.isLocked,
                onClick = { onTeamSelected(game.awayTeam) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = I_Divider,
                thickness = 0.5.dp
            )

            InfernoTeamRow(
                teamAbbr = game.homeTeam,
                teamCity = "${game.homeCity}  ·  Local",
                isSelected = selectedTeam == game.homeTeam,
                isLocked = game.isLocked,
                onClick = { onTeamSelected(game.homeTeam) }
            )
        }
    }
}

@Composable
private fun InfernoTeamRow(
    teamAbbr: String,
    teamCity: String,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) I_Red.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (!isLocked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isSelected) I_Red else Color(0xFF2E2E2E),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                teamAbbr.take(3),
                color = if (isSelected) Color.White else I_Muted,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                teamAbbr,
                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(teamCity, color = I_Muted.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(I_Red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        } else if (!isLocked) {
            Text("TAP", color = I_Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun Proposal1Preview() {
    Proposal1(onBack = {})
}
