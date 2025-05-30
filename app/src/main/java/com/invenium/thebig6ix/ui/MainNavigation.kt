// file: ui/MainNavigation.kt
package com.invenium.thebig6ix.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.invenium.thebig6ix.data.FootballFixture
import com.invenium.thebig6ix.ui.home.HomeScreen
import com.invenium.thebig6ix.ui.leaderboard.LeaderboardScreen
import com.invenium.thebig6ix.ui.predictions.PredictionScreen
import com.invenium.thebig6ix.ui.predictions.PredictionViewModel
import com.invenium.thebig6ix.ui.profile.ProfileScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import com.google.android.gms.games.leaderboard.Leaderboard
import com.invenium.thebig6ix.ui.login.LoginScreen

@Composable
fun MainNavigation(
    startDestination: String = "login",
    fixtures: List<FootballFixture> = emptyList(),
    userPoints: Int = 0
) {
    val navController = rememberNavController()
    val predictionViewModel = androidx.lifecycle.viewmodel.compose.viewModel<PredictionViewModel>()

    val screens = listOf(
        Screen("home", "Home", Icons.Default.Home),
        Screen("predictions", "Predict", Icons.Default.Notifications),
        Screen("leaderboard", "Leaders", Icons.Default.Star),
        Screen("profile", "Profile", Icons.Default.AccountCircle)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != "login"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }
            composable("home") {
                HomeScreen(fixtures = fixtures, userPoints = userPoints)
            }
            composable("predictions") {
                PredictionScreen(fixtures = fixtures) { fixtureId, home, away ->
                    predictionViewModel.submitPrediction(fixtureId, home, away)
                }
            }
            composable("leaderboard") {
                LeaderboardScreen()
            }
            composable("profile") {
                ProfileScreen()
            }
        }
    }
}


data class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
