package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

private val RESPONSIVE_CARD_MAX_WIDTH = 600.dp

/**
 * Caps content at a comfortable reading width on MEDIUM/EXPANDED screens
 * (600dp is the MEDIUM breakpoint floor, so MEDIUM devices fill nearly all
 * of their width while EXPANDED devices get a visibly capped, centered
 * column) — a no-op on COMPACT, i.e. every phone today. Pair with
 * `horizontalAlignment = Alignment.CenterHorizontally` on the parent list
 * so a narrower-than-parent item centers instead of hugging the start edge.
 */
fun Modifier.responsiveCardWidth(): Modifier = composed {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrWider = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (isMediumOrWider) this.widthIn(max = RESPONSIVE_CARD_MAX_WIDTH) else this
}
