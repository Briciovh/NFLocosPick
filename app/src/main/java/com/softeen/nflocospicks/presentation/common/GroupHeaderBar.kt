package com.softeen.nflocospicks.presentation.common

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.GlobalGroupConstants
import com.softeen.nflocospicks.domain.model.Group
import com.softeen.nflocospicks.presentation.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Fila delgada con la identidad del grupo (avatar + nombre), para usarse DEBAJO del
 * TopAppBar propio de cada tab (Picks/Leaderboard/Board) — a diferencia de un TopAppBar,
 * no fuerza un alto mínimo, así que solo ocupa el espacio que su contenido necesita.
 * Muestra el lápiz de edición solo si [currentUserId] es el creador del grupo.
 */
@Composable
fun GroupHeaderBar(
    group: Group?,
    currentUserId: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val canEdit = group != null && currentUserId != null && group.createdBy == currentUserId
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.group_code_copied)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.header)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupAvatar(
            photoUrl     = group?.photoUrl,
            iconId       = group?.iconId,
            name         = group?.name.orEmpty(),
            size         = 28.dp,
            localIconRes = if (group?.id == GlobalGroupConstants.GROUP_ID) R.drawable.nflocos_ball_avatar else null
        )
        Text(
            text       = group?.name.orEmpty(),
            color      = appColors.primary,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(start = 12.dp).weight(1f)
        )
        if (group != null) {
            val shareMessage = stringResource(R.string.group_invite_share_text, group.inviteCode)
            Box(
                modifier          = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("invite_code", group.inviteCode)))
                        }
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                    .testTag(TestTags.GROUPS_COPY_CODE_BUTTON),
                contentAlignment  = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_group_code),
                    tint               = appColors.primary,
                    modifier           = Modifier.size(14.dp)
                )
            }
            Box(
                modifier          = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                    .testTag(TestTags.GROUPS_SHARE_CODE_BUTTON),
                contentAlignment  = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.cd_share_group_code),
                    tint               = appColors.primary,
                    modifier           = Modifier.size(14.dp)
                )
            }
        }
        if (canEdit) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(appColors.primary)
                    .clickable(onClick = onEditClick)
                    .testTag(TestTags.GROUPS_EDIT_PHOTO_BUTTON),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_group_photo),
                    tint               = appColors.onPrimary,
                    modifier           = Modifier.size(14.dp)
                )
            }
        }
    }
}
