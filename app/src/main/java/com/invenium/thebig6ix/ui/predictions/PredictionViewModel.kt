package com.invenium.thebig6ix.ui.predictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PredictionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _fixtures = MutableStateFlow<List<FootballFixture>>(emptyList())
    val fixtures: StateFlow<List<FootballFixture>> = _fixtures

    private val _userPredictions = MutableStateFlow<List<UserPrediction>>(emptyList())
    val userPredictions: StateFlow<List<UserPrediction>> = _userPredictions

    private val _mostPickedScoreline = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val mostPickedScoreline: StateFlow<Map<String, Pair<Int, Int>>> = _mostPickedScoreline

    init {
        fetchFixtures(forceRefresh = true)
        fetchUserPredictions(forceRefresh = true)
    }

    fun fetchFixtures(forceRefresh: Boolean = false) {
        val source = if (forceRefresh) Source.SERVER else Source.DEFAULT
        db.collection("fixtures")
            .get(source)
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { it.toObject(FootballFixture::class.java) }
                _fixtures.value = list
            }
    }

    fun refreshFixtures() {
        fetchFixtures(forceRefresh = true)
        fetchUserPredictions(forceRefresh = true)
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
                    .get(Source.SERVER)
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
                    fetchUserPredictions(forceRefresh = true)
                    onSuccess()
                } else {
                    onFailure("Prediction already submitted.")
                }
            } catch (e: Exception) {
                onFailure("Error: ${e.message}")
            }
        }
    }

    fun fetchUserPredictions(forceRefresh: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return
        val source = if (forceRefresh) Source.SERVER else Source.DEFAULT

        db.collection("predictions")
            .whereEqualTo("userId", userId)
            .get(source)
            .addOnSuccessListener { result ->
                val predictions = result.mapNotNull { doc ->
                    val fixtureId = doc.getString("fixtureId") ?: return@mapNotNull null
                    val home = doc.getLong("homeTeamGoals")?.toInt() ?: return@mapNotNull null
                    val away = doc.getLong("awayTeamGoals")?.toInt() ?: return@mapNotNull null
                    UserPrediction(fixtureId, home, away)
                }
                _userPredictions.value = predictions
                updateMostPickedScorelines(predictions)
            }
    }

    private fun updateMostPickedScorelines(predictions: List<UserPrediction>) {
        val grouped = predictions.groupBy { it.fixtureId }
        val scorelineMap = grouped.mapValues { (_, entries) ->
            entries.groupingBy { Pair(it.homeGoals, it.awayGoals) }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: Pair(0, 0)
        }
        _mostPickedScoreline.value = scorelineMap
    }

    data class UserPrediction(
        val fixtureId: String,
        val homeGoals: Int,
        val awayGoals: Int
    )

    fun updatePointsForCompletedFixtures() {
        // Optional scoring logic here
    }
}
