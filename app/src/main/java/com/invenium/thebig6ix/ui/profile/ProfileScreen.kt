package com.invenium.thebig6ix.ui.profile

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.invenium.thebig6ix.R
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser ?: return
    val context = LocalContext.current
    val storage = FirebaseStorage.getInstance()
    val scope = rememberCoroutineScope()

    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var profileImageUrl by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(0) }
    var totalPredictions by remember { mutableStateOf(0) }
    var accuracy by remember { mutableStateOf(0.0) }
    var predictions by remember { mutableStateOf(emptyList<String>()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri: Uri ->
            val ref = storage.reference.child("profile_images/${user.uid}")
            scope.launch {
                try {
                    ref.putFile(selectedUri).await()
                    val url = ref.downloadUrl.await().toString()
                    firestore.collection("users").document(user.uid).update("profileImageUrl", url)
                    profileImageUrl = url
                } catch (e: Exception) {
                    Log.e("Profile", "Upload failed", e)
                }
            }
        }
    }

    LaunchedEffect(true) {
        scope.launch {
            val uid = user.uid
            val userDoc = firestore.collection("users").document(uid).get().await()
            points = userDoc.getLong("score")?.toInt() ?: 0
            profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

            val preds = firestore.collection("predictions")
                .whereEqualTo("userId", uid).get().await()
            totalPredictions = preds.size()
            val correct = preds.documents.count { (it.getLong("totalScore") ?: 0L) > 0L }
            accuracy = if (totalPredictions > 0) (correct.toDouble() / totalPredictions) * 100 else 0.0
            predictions = preds.documents.map {
                val home = it.getLong("homeTeamGoals") ?: 0
                val away = it.getLong("awayTeamGoals") ?: 0
                val homeTeam = it.getString("homeTeam") ?: "Home"
                val awayTeam = it.getString("awayTeam") ?: "Away"
                "$homeTeam $home - $away $awayTeam"
            }
        }
    }

    fun patchExistingUsersWithPreviousRanks() {
        firestore.collection("users").get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                val userId = doc.id
                val userRef = firestore.collection("users").document(userId)
                val previousRanks = mapOf("WEEKLY" to 1000L, "MONTHLY" to 1000L, "ALL_TIME" to 1000L)
                userRef.update("previousRanks", previousRanks).addOnSuccessListener {
                    Log.d("Patch", "Updated $userId with previousRanks")
                }.addOnFailureListener {
                    Log.e("Patch", "Failed to update $userId", it)
                }
            }
        }.addOnFailureListener {
            Log.e("Patch", "Failed to get users", it)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = if (profileImageUrl.isNotBlank())
                    rememberAsyncImagePainter(profileImageUrl)
                else painterResource(id = R.drawable.ic_account),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(100.dp).clickable { launcher.launch("image/*") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("The Big 6ix", color = Color(0xFFFFD700), fontSize = 32.sp, fontFamily = ironManFont)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Name: ${user.displayName}", color = Color.White, fontSize = 16.sp, fontFamily = ironManFont)
            Text("Email: ${user.email}", color = Color.LightGray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Points: $points", color = Color.White)
            Text("Predictions Made: $totalPredictions", color = Color.White)
            Text("Accuracy: ${"%.1f".format(accuracy)}%", color = Color.White)

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { patchExistingUsersWithPreviousRanks() }) {
                Text("Patch previousRanks", fontFamily = ironManFont)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Your Predictions", color = Color.White, fontFamily = ironManFont, fontSize = 20.sp)
            predictions.forEach {
                Text(it, color = Color.LightGray, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                FirebaseAuth.getInstance().signOut()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }) {
                Text("Logout", fontFamily = ironManFont)
            }
        }
    }
}
