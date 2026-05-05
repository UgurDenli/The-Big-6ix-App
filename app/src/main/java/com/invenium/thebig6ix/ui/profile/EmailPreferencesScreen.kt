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
fun EmailPreferencesScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val ironManFont = remember { FontFamily(Font(R.font.iron_man_of_war_001c_ncv)) }
    val scope = rememberCoroutineScope()

    var generalEmails by remember { mutableStateOf(true) }
    var personalizedEmails by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val doc = firestore.collection("users").document(userId).get().await()
        generalEmails = doc.getBoolean("generalEmailsEnabled") ?: true
        personalizedEmails = doc.getBoolean("personalizedEmailsEnabled") ?: true
        loaded = true
    }

    fun save(field: String, value: Boolean) {
        scope.launch {
            firestore.collection("users").document(userId).update(field, value).await()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Email Preferences", color = Color(0xFFFFD700), fontFamily = ironManFont, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (loaded) {
                PreferenceToggle(
                    title = "General Emails",
                    description = "Receive general updates and announcements",
                    checked = generalEmails,
                    ironManFont = ironManFont,
                    onCheckedChange = {
                        generalEmails = it
                        save("generalEmailsEnabled", it)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                PreferenceToggle(
                    title = "Personalized Emails",
                    description = "Receive emails tailored to your activity",
                    checked = personalizedEmails,
                    ironManFont = ironManFont,
                    onCheckedChange = {
                        personalizedEmails = it
                        save("personalizedEmailsEnabled", it)
                    }
                )
            } else {
                CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun PreferenceToggle(
    title: String,
    description: String,
    checked: Boolean,
    ironManFont: FontFamily,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontFamily = ironManFont, fontSize = 18.sp)
            Text(description, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFFFFD700)
            )
        )
    }
}
