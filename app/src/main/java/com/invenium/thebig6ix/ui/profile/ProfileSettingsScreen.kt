package com.invenium.thebig6ix.ui.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val ironManFont = remember { FontFamily(Font(R.font.iron_man_of_war_001c_ncv)) }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var deletionPending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val doc = firestore.collection("users").document(userId).get().await()
        deletionPending = doc.getBoolean("wantsToDeleteAccount") ?: false
    }

    fun requestDeletion() {
        scope.launch {
            firestore.collection("users").document(userId).update(
                mapOf(
                    "wantsToDeleteAccount" to true,
                    "deletionRequestedAt" to Timestamp.now()
                )
            ).await()
            deletionPending = true
            Toast.makeText(context, "Account marked for deletion. You have 7 days to cancel.", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelDeletion() {
        scope.launch {
            firestore.collection("users").document(userId).update(
                mapOf(
                    "wantsToDeleteAccount" to false,
                    "deletionCancelledAt" to Timestamp.now()
                )
            ).await()
            deletionPending = false
            Toast.makeText(context, "Account deletion cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    fun logout() {
        auth.signOut()
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 40.dp)
        ) {
            Text(
                text = "Profile Settings",
                color = Color(0xFFFFD700),
                fontSize = 28.sp,
                fontFamily = ironManFont
            )

            Spacer(modifier = Modifier.height(32.dp))

            SettingItem(Icons.Default.Email, "Email Preferences", { navController.navigate("email_preferences") }, ironManFont)
            SettingItem(Icons.Default.Notifications, "Notification Settings", { navController.navigate("push_preferences") }, ironManFont)

            if (deletionPending) {
                SettingItem(Icons.Default.Close, "Cancel Deletion", { showCancelDeleteDialog = true }, ironManFont)
            } else {
                SettingItem(Icons.Default.Delete, "Request Account Deletion", { showDeleteDialog = true }, ironManFont)
            }

            SettingItem(Icons.Default.Person, "Log Out", { showLogoutDialog = true }, ironManFont)
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                confirmButton = {
                    TextButton(onClick = { requestDeletion(); showDeleteDialog = false }) {
                        Text("Request Deletion", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Color.White) }
                },
                title = { Text("Request Account Deletion", color = Color.White, fontFamily = ironManFont) },
                text = { Text("We will mark your account for deletion and it will be reviewed in 7 days. You can cancel within this period.", color = Color.White) },
                containerColor = Color.DarkGray
            )
        }

        if (showCancelDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDeleteDialog = false },
                confirmButton = {
                    TextButton(onClick = { cancelDeletion(); showCancelDeleteDialog = false }) {
                        Text("Yes, Cancel It", color = Color(0xFFFFD700))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDeleteDialog = false }) { Text("Dismiss", color = Color.White) }
                },
                title = { Text("Cancel Deletion", color = Color.White, fontFamily = ironManFont) },
                text = { Text("Are you sure you want to cancel the deletion request?", color = Color.White) },
                containerColor = Color.DarkGray
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                confirmButton = {
                    TextButton(onClick = { logout(); showLogoutDialog = false }) {
                        Text("Log Out", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = Color.White) }
                },
                title = { Text("Log Out", color = Color.White, fontFamily = ironManFont) },
                text = { Text("Are you sure you want to log out?", color = Color.White) },
                containerColor = Color.DarkGray
            )
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    font: FontFamily
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Yellow, modifier = androidx.compose.ui.Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color.White, fontSize = 20.sp, fontFamily = font)
    }
}
