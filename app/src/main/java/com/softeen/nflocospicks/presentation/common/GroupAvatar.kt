package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.annotation.DrawableRes
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Cadena de fallback del avatar de grupo: ícono local empacado → foto subida → ícono
 * predefinido → inicial del nombre. Mismo espíritu que [UserAvatar], aplicado a
 * `Group.photoUrl`/`iconId`. [localIconRes] va primero porque es para grupos de sistema
 * (ej. el grupo global, PR-16) cuyo ícono no debe poder sobreescribirse editando
 * `photoUrl`/`iconId` desde la UI de edición de grupo normal.
 */
@Composable
fun GroupAvatar(
    photoUrl: String?,
    iconId: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes localIconRes: Int? = null
) {
    val appColors = LocalAppColors.current

    when {
        localIconRes != null -> Image(
            painter            = painterResource(localIconRes),
            contentDescription = name,
            contentScale       = ContentScale.Crop,
            modifier           = modifier.size(size).clip(MaterialTheme.shapes.medium)
        )

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
