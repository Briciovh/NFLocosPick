package com.softeen.nflocospicks.presentation.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softeen.nflocospicks.presentation.theme.BSBg
import com.softeen.nflocospicks.presentation.theme.BSWhite

// Stub screen — full implementation in PR-3 (Groups & Invite System)
@Composable
fun GroupsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BSBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Groups — coming in PR-3",
            color = BSWhite
        )
    }
}
