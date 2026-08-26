package com.softeen.nflocospicks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun TrackScreenView(navController: NavHostController) {
    val viewModel: ScreenTrackingViewModel = hiltViewModel()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(route) {
        routeToScreenName(route)?.let { viewModel.trackScreen(it) }
    }
}
