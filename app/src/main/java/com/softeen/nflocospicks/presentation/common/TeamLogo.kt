package com.softeen.nflocospicks.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun TeamLogo(
    abbr: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model              = espnTeamLogoUrl(abbr),
            contentDescription = abbr,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.fillMaxSize(),
            loading = {
                Text(
                    text       = abbr,
                    color      = appColors.secondary,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            error = {
                Text(
                    text       = abbr,
                    color      = appColors.secondary,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        )
    }
}
