package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Cadena de fallback del avatar de grupo: foto subida → ícono predefinido → inicial
 * del nombre. Mismo espíritu que [UserAvatar], aplicado a `Group.photoUrl`/`iconId`.
 */
@Composable
fun GroupAvatar(
    photoUrl: String?,
    iconId: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    when {
        photoUrl != null -> AsyncImage(
            model              = photoUrl,
            contentDescription = name,
            contentScale       = ContentScale.Crop,
            modifier           = modifier.size(size).clip(MaterialTheme.shapes.medium)
        )

        iconId != null && groupIconMap.containsKey(iconId) -> Box(
            modifier         = modifier.size(size).clip(MaterialTheme.shapes.medium)
                .background(appColors.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = groupIconMap.getValue(iconId),
                contentDescription = name,
                tint               = appColors.primary,
                modifier           = Modifier.size(size * 0.6f)
            )
        }

        else -> Box(
            modifier         = modifier.size(size).clip(MaterialTheme.shapes.medium)
                .background(appColors.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = name.firstOrNull()?.uppercase() ?: "?",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = appColors.primary
            )
        }
    }
}
