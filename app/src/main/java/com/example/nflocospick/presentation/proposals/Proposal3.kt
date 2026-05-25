package com.example.nflocospick.presentation.proposals

// ═══════════════════════════════════════════════════════════════
//  PROPOSAL 3 — GOLD RUSH ⚡
//  Dark amber · Bold gold · Full-width team rows
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Palette ─────────────────────────────────────────────────────────────────
private val G_Bg       = Color(0xFF100D00)
private val G_Surface  = Color(0xFF1E1700)
private val G_Header   = Color(0xFF1A1200)
private val G_Gold     = Color(0xFFFFB800)
private val G_Orange   = Color(0xFFFF6B00)
private val G_Danger   = Color(0xFFCC2222)
private val G_Muted    = Color(0xFF6B5500)
private val G_Light    = Color(0xFFFFE082)
private val G_Dark     = Color(0xFF100D00)

@Composable
fun Proposal3(onBack: () -> Unit) {
    var picks by remember { mutableStateOf(mapOf<Int, String>()) }
    val pickedCount = picks.size
    val unpicked = mockWeekGames.count { !it.isLocked && picks[it.id] == null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G_Bg)
    ) {
        // ── Custom header ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(G_Header, G_Surface)))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                        Text("← Atrás", color = G_Gold, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "SEMANA 12",
                            color = G_Gold,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp
                        )
                        Text("TEMPORADA 2025", color = G_Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "⚡ GOLD RUSH PICKS",
                            color = G_Gold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Toca para elegir tu equipo",
                            color = G_Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    // Progress pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(G_Gold.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "$pickedCount / ${mockWeekGames.size}",
                            color = G_Gold,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // ── Warning banner ────────────────────────────────────────────────────
        if (unpicked > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(G_Danger.copy(alpha = 0.12f))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    "⚠  Te faltan $unpicked partido(s) por elegir",
                    color = G_Danger,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Games list ────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp)
        ) {
            items(mockWeekGames) { game ->
                GoldRushGameSection(
                    game = game,
                    selectedTeam = picks[game.id],
                    onTeamSelected = { team ->
                        if (!game.isLocked) picks = picks + (game.id to team)
                    }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Bottom CTA ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(G_Surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(2.dp, G_Gold),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = G_Gold)
            ) {
                Text(
                    "◈  CONFIRMAR MIS PICKS  ◈",
                    color = G_Gold,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun GoldRushGameSection(
    game: MockGame,
    selectedTeam: String?,
    onTeamSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Time + status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                game.gameTime,
                color = G_Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            when {
                game.isLocked -> Text("🔒 CERRADO", color = G_Muted, style = MaterialTheme.typography.labelSmall)
                selectedTeam != null -> Text("✓ ELEGIDO", color = G_Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        // Away team row (top, rounded top corners)
        GoldTeamRow(
            teamAbbr = game.awayTeam,
            teamCity = game.awayCity,
            label = "VISITANTE",
            isSelected = selectedTeam == game.awayTeam,
            isLocked = game.isLocked,
            topCorners = true,
            onClick = { onTeamSelected(game.awayTeam) }
        )

        // 1dp separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(G_Bg)
        )

        // Home team row (bottom, rounded bottom corners)
        GoldTeamRow(
            teamAbbr = game.homeTeam,
            teamCity = game.homeCity,
            label = "LOCAL",
            isSelected = selectedTeam == game.homeTeam,
            isLocked = game.isLocked,
            topCorners = false,
            onClick = { onTeamSelected(game.homeTeam) }
        )
    }
}

@Composable
private fun GoldTeamRow(
    teamAbbr: String,
    teamCity: String,
    label: String,
    isSelected: Boolean,
    isLocked: Boolean,
    topCorners: Boolean,
    onClick: () -> Unit
) {
    val shape = if (topCorners)
        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    else
        RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)

    val bgColor = if (isSelected) G_Gold else G_Surface
    val primaryText = if (isSelected) G_Dark else Color(0xFFCCBB00)
    val secondaryText = if (isSelected) Color(0xFF332600) else G_Muted
    val labelBg = if (isSelected) Color(0xFF332600) else G_Muted.copy(alpha = 0.25f)
    val labelText = if (isSelected) G_Light else G_Muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(if (!isLocked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label tag
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(labelBg)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                label,
                color = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                teamAbbr,
                color = primaryText,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleSmall,
                letterSpacing = 1.sp
            )
            Text(teamCity, color = secondaryText, style = MaterialTheme.typography.labelSmall)
        }

        Text(
            if (isSelected) "◈ PICK" else if (!isLocked) "TAP ▶" else "—",
            color = if (isSelected) Color(0xFF332600) else G_Muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF100D00)
@Composable
fun Proposal3Preview() {
    Proposal3(onBack = {})
}
