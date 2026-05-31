package com.softeen.nflocospicks.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun NFLocosPickTheme(
    appColors: AppColors = LocalAppColors.current,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = darkColorScheme(
        primary = appColors.primary,
        onPrimary = appColors.onPrimary,
        primaryContainer = appColors.primaryContainer,
        onPrimaryContainer = appColors.onPrimaryContainer,
        secondary = appColors.secondary,
        onSecondary = appColors.onSecondary,
        background = appColors.background,
        onBackground = appColors.onBackground,
        surface = appColors.surface,
        onSurface = appColors.onSurface,
        surfaceVariant = appColors.surfaceVariant,
        onSurfaceVariant = appColors.onSurfaceVariant
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        content = content
    )
}
