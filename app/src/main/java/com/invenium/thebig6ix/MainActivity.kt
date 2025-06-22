package com.invenium.thebig6ix

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.FootballFixture
import com.invenium.thebig6ix.databinding.ActivityMainBinding
import com.invenium.thebig6ix.ui.MainNavigation
import com.invenium.thebig6ix.ui.auth.AuthViewModel
import com.invenium.thebig6ix.ui.theme.TheBig6ixTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        setContent {
            TheBig6ixTheme {
                MainNavigation() // ← this is your Compose navigation host with BottomBar

                val authViewModel: AuthViewModel = viewModel()
                val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

                when (isAuthenticated) {
                    null -> {
                        // Show a loading indicator while authentication state is being determined
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    true -> {
                        // User is authenticated; navigate to the main content
                        MainNavigation(startDestination = "home")
                    }

                    false -> {
                        // User is not authenticated; navigate to the login screen
                        MainNavigation(startDestination = "login")
                    }
                }
            }
        }
    }
}
