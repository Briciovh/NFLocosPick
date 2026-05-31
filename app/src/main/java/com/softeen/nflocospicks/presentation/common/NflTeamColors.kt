package com.softeen.nflocospicks.presentation.common

import androidx.compose.ui.graphics.Color
import com.softeen.nflocospicks.presentation.theme.*

data class NflTeamColors(val accent: Color, val header: Color)

val defaultTeamColors = NflTeamColors(accent = BSGold, header = BSHeader)

/**
 * Derives a full AppColors palette from a team's accent and header colors.
 */
fun NflTeamColors.toAppColors(): AppColors {
    if (this == defaultTeamColors) {
        return AppColors(
            primary = BSGold,
            onPrimary = BSHeader,
            background = BSBg,
            onBackground = BSWhite,
            surface = BSSurface,
            onSurface = BSWhite,
            surfaceVariant = BSCard,
            onSurfaceVariant = BSMuted,
            primaryContainer = BSCardSelected,
            onPrimaryContainer = BSWhite,
            secondary = BSMuted,
            onSecondary = BSWhite,
            header = BSHeader
        )
    }

    // Derive shades from header (usually the darker team color)
    val bg = header.darken(0.4f)
    val surface = header.darken(0.2f)
    val card = header
    val cardSelected = accent.darken(0.2f)

    return AppColors(
        primary = accent,
        onPrimary = header,
        background = bg,
        onBackground = Color.White,
        surface = surface,
        onSurface = Color.White,
        surfaceVariant = card,
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        primaryContainer = cardSelected,
        onPrimaryContainer = Color.White,
        secondary = accent.copy(alpha = 0.7f),
        onSecondary = Color.White,
        header = header
    )
}

private fun Color.darken(factor: Float): Color = Color(
    red = red * (1 - factor),
    green = green * (1 - factor),
    blue = blue * (1 - factor),
    alpha = alpha
)

val nflTeamColorMap: Map<String, NflTeamColors> = mapOf(
    "ARI" to NflTeamColors(accent = Color(0xFF97233F), header = Color(0xFF3D0A28)),
    "ATL" to NflTeamColors(accent = Color(0xFFA71930), header = Color(0xFF1E1E1E)),
    "BAL" to NflTeamColors(accent = Color(0xFF9E7C0C), header = Color(0xFF241773)),
    "BUF" to NflTeamColors(accent = Color(0xFFC60C30), header = Color(0xFF00338D)),
    "CAR" to NflTeamColors(accent = Color(0xFF0085CA), header = Color(0xFF101820)),
    "CHI" to NflTeamColors(accent = Color(0xFFC83803), header = Color(0xFF0B162A)),
    "CIN" to NflTeamColors(accent = Color(0xFFFB4F14), header = Color(0xFF000000)),
    "CLE" to NflTeamColors(accent = Color(0xFFFF3C00), header = Color(0xFF311D00)),
    "DAL" to NflTeamColors(accent = Color(0xFF869397), header = Color(0xFF003594)),
    "DEN" to NflTeamColors(accent = Color(0xFFFB4F14), header = Color(0xFF002244)),
    "DET" to NflTeamColors(accent = Color(0xFF0076B6), header = Color(0xFF1A1A1A)),
    "GB"  to NflTeamColors(accent = Color(0xFFFFB612), header = Color(0xFF203731)),
    "HOU" to NflTeamColors(accent = Color(0xFFA71930), header = Color(0xFF03202F)),
    "IND" to NflTeamColors(accent = Color(0xFFA5ACAF), header = Color(0xFF002C5F)),
    "JAX" to NflTeamColors(accent = Color(0xFFD7A22A), header = Color(0xFF101820)),
    "KC"  to NflTeamColors(accent = Color(0xFFFFB81C), header = Color(0xFF8B0014)),
    "LAC" to NflTeamColors(accent = Color(0xFFFFC20E), header = Color(0xFF002A5E)),
    "LAR" to NflTeamColors(accent = Color(0xFFFFA300), header = Color(0xFF002244)),
    "LV"  to NflTeamColors(accent = Color(0xFFA5ACAF), header = Color(0xFF1A1A1A)),
    "MIA" to NflTeamColors(accent = Color(0xFF008E97), header = Color(0xFF005778)),
    "MIN" to NflTeamColors(accent = Color(0xFFFFC62F), header = Color(0xFF4F2683)),
    "NE"  to NflTeamColors(accent = Color(0xFFC60C30), header = Color(0xFF002244)),
    "NO"  to NflTeamColors(accent = Color(0xFFD3BC8D), header = Color(0xFF101820)),
    "NYG" to NflTeamColors(accent = Color(0xFFA71930), header = Color(0xFF0B2265)),
    "NYJ" to NflTeamColors(accent = Color(0xFFC8D8E8), header = Color(0xFF125740)),
    "PHI" to NflTeamColors(accent = Color(0xFFA5ACAF), header = Color(0xFF004C54)),
    "PIT" to NflTeamColors(accent = Color(0xFFFFB612), header = Color(0xFF1A1A1A)),
    "SEA" to NflTeamColors(accent = Color(0xFF69BE28), header = Color(0xFF002244)),
    "SF"  to NflTeamColors(accent = Color(0xFFB3995D), header = Color(0xFFAA0000)),
    "TB"  to NflTeamColors(accent = Color(0xFFD50A0A), header = Color(0xFF322F2B)),
    "TEN" to NflTeamColors(accent = Color(0xFF418FDE), header = Color(0xFF0C2340)),
    "WSH" to NflTeamColors(accent = Color(0xFFFFB612), header = Color(0xFF5A1414)),
)
