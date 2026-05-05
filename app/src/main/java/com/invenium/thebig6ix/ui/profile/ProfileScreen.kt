package com.invenium.thebig6ix.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.invenium.thebig6ix.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Gold   = Color(0xFFFFD700)
private val CardBg = Color(0xFF111111)
private val Dim    = Color(0xFF888888)

@Composable
fun ProfileScreen(navController: NavController) {
    val auth      = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val storage   = FirebaseStorage.getInstance()
    val user      = auth.currentUser ?: return
    val scope     = rememberCoroutineScope()
    val context   = LocalContext.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var profileImageUrl by remember { mutableStateOf("") }
    var displayName     by remember { mutableStateOf("User") }
    var points          by remember { mutableStateOf(0) }
    var isLoadingImage  by remember { mutableStateOf(true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val ref = storage.reference.child("profile_images/${user.uid}")
                    ref.putFile(selectedUri).await()
                    val url = ref.downloadUrl.await().toString()
                    firestore.collection("users").document(user.uid)
                        .update("profileImageUrl", url)
                        .addOnSuccessListener { profileImageUrl = url }
                } catch (e: Exception) {
                    Log.e("Profile", "Image upload failed", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val doc = firestore.collection("users").document(user.uid).get().await()
            profileImageUrl = doc.getString("profileImageUrl") ?: ""
            displayName     = doc.getString("fullName") ?: user.displayName ?: "User"
            points          = doc.getLong("score")?.toInt() ?: 0
            isLoadingImage  = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D0D0D), Color.Black)
                        )
                    )
                    .padding(top = 32.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingImage) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        } else if (profileImageUrl.isNotBlank()) {
                            Image(
                                painter = rememberAsyncImagePainter(profileImageUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_account),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(y = (-12).dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Tap to change", color = Dim, fontSize = 10.sp)
                    }

                    Text(
                        text = displayName,
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Score card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1400)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Points", color = Dim, fontSize = 12.sp)
                        Text(
                            text = "$points",
                            color = Gold,
                            fontFamily = ironManFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                    }
                    Text("🏆", fontSize = 40.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action section
            ProfileSection(title = "My Stats") {
                ProfileRow(
                    label = "Past Predictions",
                    icon = Icons.Filled.History,
                    onClick = { navController.navigate("past_predictions") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileSection(title = "Community") {
                ProfileRow(
                    label = "YouTube Channel",
                    iconRes = R.drawable.ic_notifications_black_24dp,
                    tint = Color(0xFFFF0000),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@TheBig6ix"))
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }
                )
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
                ProfileRow(
                    label = "Discord Server",
                    iconRes = R.drawable.ic_home_black_24dp,
                    tint = Color(0xFF5865F2),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/invite/gf3FqaJH5M"))
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileSection(title = "Account") {
                ProfileRow(
                    label = "Settings",
                    icon = Icons.Filled.Settings,
                    onClick = { navController.navigate("profile_settings") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title.uppercase(),
            color = Dim,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    tint: Color = Gold,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null    -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                iconRes != null -> Icon(painterResource(id = iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Dim, modifier = Modifier.size(20.dp))
    }
}
