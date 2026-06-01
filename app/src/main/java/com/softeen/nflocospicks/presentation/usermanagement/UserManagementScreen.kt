package com.softeen.nflocospicks.presentation.usermanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.softeen.nflocospicks.domain.model.User
import com.softeen.nflocospicks.domain.model.UserRole
import com.softeen.nflocospicks.presentation.preview.PreviewWrapper
import com.softeen.nflocospicks.presentation.preview.fakeUser
import com.softeen.nflocospicks.presentation.theme.AppColors
import com.softeen.nflocospicks.presentation.theme.LocalAppColors

@Composable
fun UserManagementScreen(
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    viewModel: UserManagementViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsStateWithLifecycle()

    UserManagementScreenContent(
        users          = users,
        currentUserUid = currentUserUid,
        onNavigateBack = onNavigateBack,
        onSetRole      = { uid, role -> viewModel.setRole(uid, role) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserManagementScreenContent(
    users: List<User>,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSetRole: (uid: String, role: UserRole) -> Unit
) {
    val appColors = LocalAppColors.current
    var dialogUser by remember { mutableStateOf<User?>(null) }

    Scaffold(
        containerColor = appColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Gestión de usuarios",
                        color      = appColors.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint               = appColors.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.header)
            )
        }
    ) { innerPadding ->
        if (users.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = "Sin usuarios registrados",
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(users, key = { it.uid }) { user ->
                    UserRow(
                        user           = user,
                        isCurrentUser  = user.uid == currentUserUid,
                        appColors      = appColors,
                        onClick        = { dialogUser = user }
                    )
                }
            }
        }
    }

    dialogUser?.let { user ->
        RoleDialog(
            user      = user,
            appColors = appColors,
            onSelect  = { role ->
                onSetRole(user.uid, role)
                dialogUser = null
            },
            onDismiss = { dialogUser = null }
        )
    }
}

@Composable
private fun UserRow(
    user: User,
    isCurrentUser: Boolean,
    appColors: AppColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user = user, appColors = appColors)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = user.displayName,
                    color      = appColors.onBackground,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCurrentUser) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "(Tú)",
                        color = appColors.secondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text  = user.email,
                color = appColors.secondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        RoleBadge(role = user.role, appColors = appColors)
    }
}

@Composable
private fun UserAvatar(user: User, appColors: AppColors) {
    if (user.photoUrl != null) {
        AsyncImage(
            model              = user.photoUrl,
            contentDescription = user.displayName,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(40.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier         = Modifier.size(40.dp).clip(CircleShape).background(appColors.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color      = appColors.header,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun RoleBadge(role: UserRole, appColors: AppColors) {
    val (bgColor, textColor) = when (role) {
        UserRole.INSIDER -> appColors.primary.copy(alpha = 0.15f) to appColors.primary
        UserRole.REGULAR -> appColors.secondary.copy(alpha = 0.15f) to appColors.secondary
    }
    Box(
        modifier         = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = role.name,
            color      = textColor,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoleDialog(
    user: User,
    appColors: AppColors,
    onSelect: (UserRole) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Cambiar rol",
                color      = appColors.onBackground,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text  = user.displayName,
                    color = appColors.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
                UserRole.entries.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(role) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = user.role == role,
                            onClick  = { onSelect(role) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text  = roleLabel(role),
                            color = appColors.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = appColors.secondary)
            }
        },
        containerColor     = appColors.surface,
        titleContentColor  = appColors.onSurface,
        textContentColor   = appColors.onSurface
    )
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.REGULAR -> "Regular"
    UserRole.INSIDER -> "Insider"
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewUsers = listOf(
    fakeUser,
    fakeUser.copy(uid = "user_2", displayName = "Juan García",  email = "juan@example.com"),
    fakeUser.copy(uid = "user_3", displayName = "María López",  email = "maria@example.com", role = UserRole.INSIDER),
    fakeUser.copy(uid = "user_4", displayName = "Carlos Pérez", email = "carlos@example.com")
)

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun UserManagementScreenPreview() {
    PreviewWrapper {
        UserManagementScreenContent(
            users          = previewUsers,
            currentUserUid = fakeUser.uid,
            onNavigateBack = {},
            onSetRole      = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B2156)
@Composable
private fun UserManagementEmptyPreview() {
    PreviewWrapper {
        UserManagementScreenContent(
            users          = emptyList(),
            currentUserUid = fakeUser.uid,
            onNavigateBack = {},
            onSetRole      = { _, _ -> }
        )
    }
}
