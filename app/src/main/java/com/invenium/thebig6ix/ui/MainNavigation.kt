package com.invenium.thebig6ix.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import java.util.Date

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val startDestination = remember {
        if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"
    }

    // Only show splash for already-logged-in users (not the login/onboarding flow)
    val isLoggedIn = remember { FirebaseAuth.getInstance().currentUser != null }
    var showSplash by remember { mutableStateOf(isLoggedIn) }

    val predictionViewModel: PredictionViewModel = viewModel()
    val pfFixtures by predictionViewModel.fixtures.collectAsState()
    val pfPredictions by predictionViewModel.userPredictions.collectAsState()
    val hasUnpredicted = remember(pfFixtures, pfPredictions) {
        val now = Date()
        pfFixtures.any { f ->
            f.deadline?.toDate()?.after(now) == true &&
            pfPredictions.none { it.fixtureId == f.id }
        }
    }

    val screens = listOf(
        Screen("home", "Home", Icons.Filled.Home),
        Screen("predictions", "Predict", Icons.Filled.SportsSoccer),
        Screen("profile", "Profile", Icons.Filled.Person)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "predictions", "profile")

    Box(modifier = Modifier.fillMaxSize()) {

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black, tonalElevation = 8.dp) {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                if (screen.route == "predictions" && hasUnpredicted) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = Color(0xFFFFD700), modifier = Modifier.size(8.dp)) {}
                                    }) {
                                        Icon(screen.icon, contentDescription = screen.label, tint = if (selected) Color(0xFFFFD700) else Color.White)
                                    }
                                } else {
                                    Icon(screen.icon, contentDescription = screen.label, tint = if (selected) Color(0xFFFFD700) else Color.White)
                                }
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

    // Splash overlay — sits on top of the Scaffold, fades out when done
    AnimatedVisibility(
        visible = showSplash,
        enter   = androidx.compose.animation.EnterTransition.None,
        exit    = fadeOut(animationSpec = tween(380))
    ) {
        SplashScreen(onFinished = { showSplash = false })
    }

    } // end outer Box
}

data class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
