package com.invenium.thebig6ix

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.invenium.thebig6ix.ui.MainNavigation
import com.invenium.thebig6ix.ui.login.DiscordAuthViewModel
import com.invenium.thebig6ix.ui.theme.TheBig6ixTheme

class MainActivity : ComponentActivity() {

    private val discordAuthViewModel: DiscordAuthViewModel by viewModels()

    /** Route to navigate to when a notification is tapped (predictions / profile). */
    private var notificationRoute by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — FCM still works for existing token */ }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        askNotificationPermission()
        saveFcmToken()

        // Handle cold-start notification tap
        notificationRoute = intent?.getStringExtra("navigate_to")

        setContent {
            TheBig6ixTheme {
                MainNavigation(notificationDeepLink = notificationRoute)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun saveFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Handle notification tap while app is already running
        intent.getStringExtra("navigate_to")?.let { route ->
            notificationRoute = route
        }

        // Handle Discord OAuth deep link
        val data = intent.data ?: return
        if (data.scheme == "big6ix" && data.host == "auth") {
            val success = data.getQueryParameter("success")
            if (success == "true") {
                val token = data.getQueryParameter("token") ?: return
                val name  = data.getQueryParameter("name") ?: ""
                discordAuthViewModel.onDiscordCallback(token, name)
            } else {
                val error = data.getQueryParameter("error") ?: "Access denied."
                discordAuthViewModel.onDiscordError(error)
            }
        }
    }
}
