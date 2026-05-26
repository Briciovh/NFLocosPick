package com.softeen.nflocospicks.presentation.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softeen.nflocospicks.R

@Composable
fun WelcomeScreen(
    onNavigateToProposal1: () -> Unit,
    onNavigateToProposal2: () -> Unit,
    onNavigateToProposal3: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.nflocos_picks_logo),
            contentDescription = "NFL Locos Picks Logo",
            modifier = Modifier.size(200.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Elige tu estilo de picks",
            color = Color(0xFF888888),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // ── Proposal 1: Inferno ───────────────────────────────────────────────
        ProposalCard(
            emoji = "🔥",
            title = "INFERNO",
            subtitle = "Oscuro · Energía de fuego · Naranja",
            accentColor = Color(0xFFFF6B00),
            cardBg = Color(0xFF1A1A1A),
            onClick = onNavigateToProposal1
        )

        Spacer(Modifier.height(12.dp))

        // ── Proposal 2: Blue Steel ────────────────────────────────────────────
        ProposalCard(
            emoji = "🏈",
            title = "BLUE STEEL",
            subtitle = "Azul cobalto · Premium · Marcador",
            accentColor = Color(0xFF4A7AD5),
            cardBg = Color(0xFF0D1F4D),
            onClick = onNavigateToProposal2
        )

        Spacer(Modifier.height(12.dp))

        // ── Proposal 3: Gold Rush ─────────────────────────────────────────────
        ProposalCard(
            emoji = "⚡",
            title = "GOLD RUSH",
            subtitle = "Dorado · Audaz · Estilo deportivo",
            accentColor = Color(0xFFFFB800),
            cardBg = Color(0xFF1E1700),
            onClick = onNavigateToProposal3
        )
    }
}

@Composable
private fun ProposalCard(
    emoji: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    cardBg: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 28.sp)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 1.sp
                )
                Text(
                    subtitle,
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("▶", color = accentColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen({}, {}, {})
}
