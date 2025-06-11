// file: ui/MainNavigation.kt
package com.invenium.thebig6ix.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.invenium.thebig6ix.data.FootballFixture
import com.invenium.thebig6ix.ui.home.HomeScreen
import com.invenium.thebig6ix.ui.leaderboard.LeaderboardScreen
import com.invenium.thebig6ix.ui.predictions.PredictionScreen
import com.invenium.thebig6ix.ui.predictions.PredictionViewModel
import com.invenium.thebig6ix.ui.profile.ProfileScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.ui.home.HomeViewModel
import com.invenium.thebig6ix.ui.login.LoginScreen
import com.invenium.thebig6ix.ui.profile.PastPredictionsScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainNavigation(
    startDestination: String = "login",
    fixtures: List<FootballFixture> = emptyList(),
    userPoints: Int = 0
) {
    val navController = rememberNavController()
    val predictionViewModel = viewModel<PredictionViewModel>()

    val screens = listOf(
        Screen("home", "Home", Icons.Default.Home),
        Screen("predictions", "Predict", Icons.Default.Notifications),
        Screen("leaderboard", "Leaderboard", Icons.Default.Star),
        Screen("profile", "Profile", Icons.Default.AccountCircle)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != "login"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.Black, // dark background
                    tonalElevation = 8.dp
                ) {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.label,
                                    tint = if (selected) Color(0xFFFFD700) else Color.White // yellow if selected
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    color = if (selected) Color(0xFFFFD700) else Color.White // yellow if selected
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.DarkGray // subtle background highlight for selected
                            )
                        )
                    }
                }
            }
        }
    )
    { innerPadding ->
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
                HomeScreen(userPoints = userPoints)
            }
            composable("predictions") {
                val predictionViewModel = viewModel<PredictionViewModel>()
                val homeViewModel = viewModel<HomeViewModel>()
                val fixtures = homeViewModel.fixtures.collectAsState().value

                PredictionScreen(
                    fixtures = fixtures,
                    viewModel = predictionViewModel
                )
            }
            composable("leaderboard") {
                LeaderboardScreen()
            }
            composable("profile") {
                ProfileScreen(navController = navController)
            }
            composable("past_predictions") {
                PastPredictionsScreen()
            }
        }
    }
}


data class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
