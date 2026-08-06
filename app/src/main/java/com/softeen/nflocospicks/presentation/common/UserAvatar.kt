package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

/**
 * Shared user-avatar fallback chain: real photo → favorite-team-colored circle with a
 * silhouette icon → neutral circle with a silhouette icon. Never renders the team's logo
 * directly, so it's never mistaken for that team's official account.
 */
@Composable
fun UserAvatar(
    photoUrl: String?,
    displayName: String,
    favoriteTeamAbbr: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val teamColor = nflTeamColorMap[favoriteTeamAbbr]?.accent

    when {
        photoUrl != null -> AsyncImage(
            model              = photoUrl,
            contentDescription = displayName,
            contentScale       = ContentScale.Crop,
            modifier           = modifier.size(size).clip(CircleShape)
        )

        teamColor != null -> Box(
            modifier         = modifier.size(size).clip(CircleShape).background(teamColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.Person,
                contentDescription = displayName,
                tint               = Color.White,
                modifier           = Modifier.size(size * 0.6f)
            )
        }

        else -> Box(
            modifier         = modifier.size(size).clip(CircleShape).background(appColors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.Person,
                contentDescription = displayName,
                tint               = appColors.onSurfaceVariant,
                modifier           = Modifier.size(size * 0.6f)
            )
        }
    }
}
