package com.softeen.nflocospicks.presentation.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.BoardMessage
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

private const val MAX_VISIBLE_MESSAGES = 3

/**
 * Panel fijo de solo lectura (PR-19) con los últimos mensajes del board del grupo
 * global, mostrado al fondo de GroupsScreen. Sin scroll propio — a diferencia del
 * board completo, que se abre con [onClick]. Reutiliza el mismo flujo de datos
 * (WatchBoardMessagesUseCase, vía GroupViewModel) que BoardScreen usa para el board
 * completo, sin reimplementar nada de lectura de Firestore.
 */
@Composable
fun GlobalGroupFeedPanel(
    messages: List<BoardMessage>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val latest = remember(messages) { messages.sortedByDescending { it.timestamp }.take(MAX_VISIBLE_MESSAGES) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = stringResource(R.string.global_group_feed_title),
                    color      = appColors.primary,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.titleSmall,
                    modifier   = Modifier.weight(1f)
                )
                Icon(
                    imageVector        = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint               = appColors.secondary
                )
            }
            Spacer(Modifier.height(8.dp))
            if (latest.isEmpty()) {
                Text(
                    text  = stringResource(R.string.global_group_feed_empty),
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    latest.forEach { message -> FeedMessageRow(message) }
                }
            }
        }
    }
}

@Composable
private fun FeedMessageRow(message: BoardMessage) {
    val appColors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.isAnnouncement) {
            Icon(
                imageVector        = Icons.Filled.Campaign,
                contentDescription = null,
                tint               = appColors.primary,
                modifier           = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text       = message.senderName,
            color      = appColors.onSurface,
            fontWeight = FontWeight.Bold,
            style      = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text      = message.content,
            color     = appColors.secondary,
            style     = MaterialTheme.typography.bodySmall,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier.weight(1f)
        )
    }
}
