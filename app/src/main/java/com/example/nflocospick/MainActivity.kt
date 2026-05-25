package com.example.nflocospick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nflocospick.presentation.navigation.NavGraph
import com.example.nflocospick.presentation.theme.NFLocosPickTheme
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
