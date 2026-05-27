package com.softeen.nflocospicks.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Blue Steel is the single, forced color scheme for the entire app.
// Dynamic color (Material You) is intentionally disabled — this is a designed theme.
private val BlueSteelColorScheme = darkColorScheme(
    primary                = BSGold,
    onPrimary              = BSHeader,
    primaryContainer       = BSCardSelected,
    onPrimaryContainer     = BSWhite,
    secondary              = BSMuted,
    onSecondary            = BSWhite,
    background             = BSBg,
    onBackground           = BSWhite,
    surface                = BSSurface,
    onSurface              = BSWhite,
    surfaceVariant         = BSCard,
    onSurfaceVariant       = BSMuted
)

@Composable
fun NFLocosPickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueSteelColorScheme,
        typography  = Typography,
        content     = content
    )
}
