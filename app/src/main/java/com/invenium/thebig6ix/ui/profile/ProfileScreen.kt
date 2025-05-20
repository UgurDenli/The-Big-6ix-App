package com.invenium.thebig6ix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    var points by remember { mutableStateOf(0) }
    var totalPredictions by remember { mutableStateOf(0) }
    var accuracy by remember { mutableStateOf(0.0) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(true) {
        scope.launch {
            val uid = user?.uid ?: return@launch

            val userDoc = firestore.collection("users").document(uid).get().await()
            points = userDoc.getLong("totalScore")?.toInt() ?: 0

            val predictions = firestore.collection("predictions")
                .whereEqualTo("userId", uid).get().await()
            totalPredictions = predictions.size()
            val correct = predictions.documents.count {
                (it.getLong("totalScore") ?: 0L) > 0L
            }
            accuracy = if (totalPredictions > 0) (correct.toDouble() / totalPredictions) * 100 else 0.0
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your Profile", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Name: ${user?.displayName ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
        Text("Email: ${user?.email ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Total Points: $points")
        Text("Predictions Made: $totalPredictions")
        Text("Accuracy: ${"%.1f".format(accuracy)}%", textAlign = TextAlign.Center)
    }
}
