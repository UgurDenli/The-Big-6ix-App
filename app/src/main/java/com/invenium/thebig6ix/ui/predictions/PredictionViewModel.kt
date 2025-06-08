package com.invenium.thebig6ix.ui.predictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PredictionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _userPredictions = MutableStateFlow<List<UserPrediction>>(emptyList())
    val userPredictions: StateFlow<List<UserPrediction>> = _userPredictions

    init {
        fetchUserPredictions()
    }

    fun submitPredictionIfNotExists(
        fixtureId: String,
        homeTeam: String,
        awayTeam: String,
        homeGoals: Int,
        awayGoals: Int,
        gameWeek: Int = 1,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure("User not logged in")
        val predictionsRef = db.collection("predictions")

        viewModelScope.launch {
            try {
                val existing = predictionsRef
                    .whereEqualTo("fixtureId", fixtureId)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                if (existing.isEmpty) {
                    val prediction = mapOf(
                        "fixtureId" to fixtureId,
                        "homeTeam" to homeTeam,
                        "awayTeam" to awayTeam,
                        "homeTeamGoals" to homeGoals,
                        "awayTeamGoals" to awayGoals,
                        "userId" to userId,
                        "scoredPoints" to false,
                        "gameweek" to gameWeek
                    )

                    predictionsRef.add(prediction).await()
                    fetchUserPredictions()
                    onSuccess()
                } else {
                    onFailure("Prediction already submitted.")
                }
            } catch (e: Exception) {
                onFailure("Error: \${e.message}")
            }
        }
    }

    fun fetchUserPredictions() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("predictions")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                val predictions = result.mapNotNull { doc ->
                    val fixtureId = doc.getString("fixtureId") ?: return@mapNotNull null
                    val home = doc.getLong("homeTeamGoals")?.toInt() ?: return@mapNotNull null
                    val away = doc.getLong("awayTeamGoals")?.toInt() ?: return@mapNotNull null
                    UserPrediction(fixtureId, home, away)
                }
                _userPredictions.value = predictions
            }
    }

    data class UserPrediction(
        val fixtureId: String,
        val homeGoals: Int,
        val awayGoals: Int
    )

    fun updatePointsForCompletedFixtures() {
        // Optional logic to update points later
    }
}
