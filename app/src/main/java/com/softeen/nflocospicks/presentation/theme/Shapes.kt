package com.softeen.nflocospicks.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// medium is bumped from the app's previous de-facto 12dp card radius to
// 16dp — cards overwhelmingly pad their content at 16dp, so the corner now
// tracks the padding instead of visually cutting into it.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
