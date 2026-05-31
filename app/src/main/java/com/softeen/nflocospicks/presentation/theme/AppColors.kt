package com.softeen.nflocospicks.presentation.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val primary: Color,            // replaces BSGold
    val onPrimary: Color,          // usually BSHeader or BSWhite
    val background: Color,         // replaces BSBg
    val onBackground: Color,       // replaces BSWhite
    val surface: Color,            // replaces BSSurface
    val onSurface: Color,          // replaces BSWhite
    val surfaceVariant: Color,     // replaces BSCard
    val onSurfaceVariant: Color,   // replaces BSMuted
    val primaryContainer: Color,   // replaces BSCardSelected
    val onPrimaryContainer: Color, // replaces BSWhite
    val secondary: Color,          // replaces BSMuted
    val onSecondary: Color,        // replaces BSWhite
    val header: Color              // replaces BSHeader
)

val LocalAppColors = compositionLocalOf { 
    AppColors(
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
