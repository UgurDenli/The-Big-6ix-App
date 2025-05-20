package com.invenium.thebig6ix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.invenium.thebig6ix.ui.theme.TheBig6ixTheme

class MainComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TheBig6ixTheme {
                MainNavigation(fixtures = emptyList(), userPoints = 0) // Replace with real data
            }
        }
    }
}