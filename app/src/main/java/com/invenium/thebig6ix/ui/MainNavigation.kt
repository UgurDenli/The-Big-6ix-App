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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.notifications.DeadlineReminderWorker
import com.invenium.thebig6ix.ui.home.HomeScreen
import com.invenium.thebig6ix.ui.leagues.HeadToHeadPickerScreen
import com.invenium.thebig6ix.ui.leagues.HeadToHeadScreen
import com.invenium.thebig6ix.ui.leagues.MiniLeagueViewModel
import com.invenium.thebig6ix.ui.seasons.SeasonArchiveScreen
import com.invenium.thebig6ix.ui.seasons.SeasonLeaderboardScreen
import com.invenium.thebig6ix.ui.seasons.SeasonPredictionsScreen
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
fun MainNavigation(notificationDeepLink: String? = null) {
    val navController = rememberNavController()
    val context       = LocalContext.current
    val startDestination = remember {
        if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"
    }

    val isLoggedIn = remember { FirebaseAuth.getInstance().currentUser != null }
    var showSplash by remember { mutableStateOf(isLoggedIn) }

    // Navigate to the notification target once the nav graph is ready
    LaunchedEffect(notificationDeepLink) {
        if (notificationDeepLink != null && isLoggedIn) {
            navController.navigate(notificationDeepLink) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    // Schedule deadline reminder once per app session
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) DeadlineReminderWorker.schedule(context)
    }

    val predictionViewModel: PredictionViewModel = viewModel()
    val miniLeagueViewModel: MiniLeagueViewModel = viewModel()

    val pfFixtures    by predictionViewModel.fixtures.collectAsState()
    val pfPredictions by predictionViewModel.userPredictions.collectAsState()
    val hasUnpredicted = remember(pfFixtures, pfPredictions) {
        val now = Date()
        pfFixtures.any { f ->
            f.deadline?.toDate()?.after(now) == true &&
            pfPredictions.none { it.fixtureId == f.id }
        }
    }

    val screens = listOf(
        Screen("home",        "Home",    Icons.Filled.Home),
        Screen("predictions", "Predict", Icons.Filled.SportsSoccer),
        Screen("profile",     "Profile", Icons.Filled.Person)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute      = navBackStackEntry?.destination?.route
    val showBottomBar     = currentRoute in listOf("home", "predictions", "profile")

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
                                            Icon(screen.icon, screen.label, tint = if (selected) Color(0xFFFFD700) else Color.White)
                                        }
                                    } else {
                                        Icon(screen.icon, screen.label, tint = if (selected) Color(0xFFFFD700) else Color.White)
                                    }
                                },
                                label = {
                                    Text(screen.label, color = if (selected) Color(0xFFFFD700) else Color.White)
                                },
                                selected = selected,
                                onClick  = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.DarkGray)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = startDestination,
                modifier         = Modifier.padding(innerPadding)
            ) {
                composable("login") {
                    LoginScreen(onLoginSuccess = { needsOnboarding ->
                        navController.navigate(if (needsOnboarding) "onboarding" else "home") {
                            popUpTo("login") { inclusive = true }
                        }
                    })
                }

                composable("onboarding") {
                    OnboardingScreen(onFinished = {
                        navController.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                    })
                }

                composable("home") {
                    HomeScreen(onViewFullLeaderboard = { navController.navigate("leaderboard") })
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

                // Own predictions (from profile)
                composable("past_predictions") {
                    PastPredictionsScreen(
                        userId = null,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Another user's predictions (from leaderboard) — includes back + share
                composable("past_predictions/{userId}") { backStackEntry ->
                    val userId = backStackEntry.arguments?.getString("userId")
                    PastPredictionsScreen(
                        userId = userId,
                        onBack = { navController.popBackStack() }
                    )
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

                // ── Head to Head ──────────────────────────────────────────────

                composable("head_to_head_picker") {
                    HeadToHeadPickerScreen(
                        viewModel       = miniLeagueViewModel,
                        onBack          = { navController.popBackStack() },
                        onPickedOpponent = { navController.navigate("head_to_head") }
                    )
                }

                composable("head_to_head") {
                    HeadToHeadScreen(
                        viewModel = miniLeagueViewModel,
                        onBack    = { navController.popBackStack() }
                    )
                }

                composable("season_archive") {
                    SeasonArchiveScreen(
                        onBack           = { navController.popBackStack() },
                        onSeasonSelected = { id, label ->
                            navController.navigate("season_leaderboard/$id/${label.replace("/", "|")}")
                        }
                    )
                }

                composable("season_leaderboard/{seasonId}/{seasonLabel}") { backStack ->
                    val seasonId    = backStack.arguments?.getString("seasonId")    ?: ""
                    val seasonLabel = (backStack.arguments?.getString("seasonLabel") ?: "Season")
                        .replace("|", "/")
                    SeasonLeaderboardScreen(
                        seasonId       = seasonId,
                        seasonLabel    = seasonLabel,
                        onBack         = { navController.popBackStack() },
                        onUserSelected = { userId, userName ->
                            navController.navigate(
                                "season_predictions/$seasonId/${seasonLabel.replace("/", "|")}/$userId/${userName.replace("/", "|")}"
                            )
                        }
                    )
                }

                composable("season_predictions/{seasonId}/{seasonLabel}/{userId}/{userName}") { backStack ->
                    val seasonId    = backStack.arguments?.getString("seasonId")    ?: ""
                    val seasonLabel = (backStack.arguments?.getString("seasonLabel") ?: "Season").replace("|", "/")
                    val userId      = backStack.arguments?.getString("userId")      ?: ""
                    val userName    = (backStack.arguments?.getString("userName")   ?: "").replace("|", "/")
                    SeasonPredictionsScreen(
                        seasonId   = seasonId,
                        seasonName = seasonLabel,
                        userId     = userId,
                        userName   = userName,
                        onBack     = { navController.popBackStack() }
                    )
                }
            }
        }

        // Splash overlay
        AnimatedVisibility(
            visible = showSplash,
            enter   = androidx.compose.animation.EnterTransition.None,
            exit    = fadeOut(animationSpec = tween(380))
        ) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}

data class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
