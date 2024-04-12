package com.invenium.thebig6ix.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class HomeViewModel : ViewModel() {

    private val db: FirebaseFirestore by lazy {
        Firebase.firestore
    }

    private val predictionsCollection = db.collection("predictions")
    private val leaderboardCollection = db.collection("leaderboard")

    suspend fun performBatchProcessing(): Boolean {
        val batch = db.batch()

        try {
            // Retrieve predictions
            val querySnapshot: QuerySnapshot = predictionsCollection.get().await()

            // Iterate through predictions
            for (document in querySnapshot.documents) {
                // Get prediction data
                val isCorrect = document.getBoolean("isCorrect") ?: false
                val userId = document.getString("userId") ?: ""

                // Calculate points based on prediction correctness
                val pointsEarned = if (isCorrect) {
                    10 // Example: 10 points for correct prediction
                } else {
                    0 // No points earned for incorrect prediction
                }

                // Get user document reference
                val userDocRef = leaderboardCollection.document(userId)

                // Update user score in the batch
                batch.update(userDocRef, "score", FieldValue.increment(pointsEarned.toDouble()))
            }

            // Commit the batch
            batch.commit().await()

            return true // Batch operation successful
        } catch (e: Exception) {
            // Error occurred during batch processing
            return false
        }
    }
}
