package com.softeen.nflocospicks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.softeen.nflocospicks.presentation.navigation.NavGraph
import com.softeen.nflocospicks.presentation.theme.NFLocosPickTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NFLocosPickTheme {
                NavGraph()
            }
        }
    }
}
