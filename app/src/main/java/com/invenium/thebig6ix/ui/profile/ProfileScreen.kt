package com.invenium.thebig6ix.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun ProfileScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser ?: return
    val storage = FirebaseStorage.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var profileImageUrl by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("User") }
    var points by remember { mutableStateOf(0) }
    var isLoadingImage by remember { mutableStateOf(true) }

    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv))

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
            displayName = doc.getString("fullName") ?: user.displayName ?: "User"
            points = doc.getLong("score")?.toInt() ?: 0
            isLoadingImage = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile image
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable { launcher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingImage) {
                    CircularProgressIndicator(color = Color.Yellow)
                } else {
                    if (profileImageUrl.isNotBlank()) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Welcome, $displayName", color = Color.White, fontSize = 22.sp, fontFamily = ironManFont)
            Text("Current Points: $points", color = Color.Yellow, fontSize = 22.sp, fontFamily = ironManFont)

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { navController.navigate("past_predictions") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Past Predictions", color = Color.Yellow, fontFamily = ironManFont, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@TheBig6ix")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visit our YouTube Channel", color = Color.Yellow, fontFamily = ironManFont, fontSize = 20.sp)
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/invite/gf3FqaJH5M")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Join the Community Discord", color = Color.Yellow, fontFamily = ironManFont, fontSize = 20.sp)
            }


            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate("profile_settings") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("General Settings", color = Color.Yellow, fontFamily = ironManFont, fontSize = 20.sp)
            }
        }
    }
}
