package com.invenium.thebig6ix

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isAuthenticated: Boolean = false // Track authentication status

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // Remove ActionBar back button
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // Check if user is already authenticated
        checkAuthentication()
    }

    // Check if user is authenticated
    private fun checkAuthentication() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        isAuthenticated = currentUser != null
        if (!isAuthenticated) {
            // If not authenticated, navigate to the login fragment
            findNavController(R.id.nav_host_fragment_activity_main).navigate(R.id.navigation_login)
        }
    }

    // Ensure Up navigation is handled properly
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
