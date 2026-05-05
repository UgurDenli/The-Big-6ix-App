package com.invenium.thebig6ix.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.ui.home.HomeScreen
import com.invenium.thebig6ix.ui.predictions.PredictionScreen
import com.invenium.thebig6ix.ui.predictions.PredictionViewModel
import com.invenium.thebig6ix.ui.profile.ProfileScreen
import com.invenium.thebig6ix.ui.login.LoginScreen
import com.invenium.thebig6ix.ui.profile.PastPredictionsScreen
import com.invenium.thebig6ix.ui.profile.ProfileSettingsScreen
import com.invenium.thebig6ix.ui.profile.EmailPreferencesScreen
import com.invenium.thebig6ix.ui.profile.PushNotificationsScreen
import com.invenium.thebig6ix.ui.onboarding.OnboardingScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val startDestination = remember {
        if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"
    }

    val screens = listOf(
        Screen("home", "Home", Icons.Filled.Home),
        Screen("predictions", "Predict", Icons.Filled.SportsSoccer),
        Screen("profile", "Profile", Icons.Filled.Person)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "predictions", "profile")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black, tonalElevation = 8.dp) {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.label,
                                    tint = if (selected) Color(0xFFFFD700) else Color.White
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    color = if (selected) Color(0xFFFFD700) else Color.White
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
                                indicatorColor = Color.DarkGray
                            )
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
                LoginScreen(onLoginSuccess = { needsOnboarding ->
                    val dest = if (needsOnboarding) "onboarding" else "home"
                    navController.navigate(dest) {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }
            composable("onboarding") {
                OnboardingScreen(onFinished = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                })
            }
            composable("home") {
                HomeScreen(onViewFullLeaderboard = {
                    navController.navigate("leaderboard")
                })
            }
            composable("predictions") {
                val predictionViewModel = viewModel<PredictionViewModel>()
                PredictionScreen(viewModel = predictionViewModel)
            }
            composable("leaderboard") {
                com.invenium.thebig6ix.ui.leaderboard.LeaderboardScreen(
                    onBack = { navController.popBackStack() },
                    onViewUserPredictions = { userId ->
                        navController.navigate("past_predictions/$userId")
                    }
                )
            }
            composable("profile") {
                ProfileScreen(navController = navController)
            }
            composable("past_predictions") {
                PastPredictionsScreen(userId = null)
            }
            composable("past_predictions/{userId}") { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId")
                PastPredictionsScreen(userId = userId)
            }
            composable("profile_settings") {
                ProfileSettingsScreen(navController = navController)
            }
            composable("email_preferences") {
                EmailPreferencesScreen(onBack = { navController.popBackStack() })
            }
            composable("push_preferences") {
                PushNotificationsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

data class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
