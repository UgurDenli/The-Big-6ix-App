package com.invenium.thebig6ix

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.invenium.thebig6ix.ui.MainNavigation
import com.invenium.thebig6ix.ui.login.DiscordAuthViewModel
import com.invenium.thebig6ix.ui.theme.TheBig6ixTheme

class MainActivity : ComponentActivity() {

    private val discordAuthViewModel: DiscordAuthViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        setContent {
            TheBig6ixTheme {
                MainNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
