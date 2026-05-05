package com.invenium.thebig6ix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PushNotificationsScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val ironManFont = remember { FontFamily(Font(R.font.iron_man_of_war_001c_ncv)) }
    val scope = rememberCoroutineScope()

    var pushEnabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val doc = firestore.collection("users").document(userId).get().await()
        pushEnabled = doc.getBoolean("pushNotificationsEnabled") ?: true
        loaded = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification Settings", color = Color(0xFFFFD700), fontFamily = ironManFont, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (loaded) {
                PreferenceToggle(
                    title = "Enable Push Notifications",
                    description = "Receive deadline reminders and score updates",
                    checked = pushEnabled,
                    ironManFont = ironManFont,
                    onCheckedChange = {
                        pushEnabled = it
                        scope.launch {
                            firestore.collection("users").document(userId)
                                .update("pushNotificationsEnabled", it).await()
                        }
                    }
                )
            } else {
                CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
