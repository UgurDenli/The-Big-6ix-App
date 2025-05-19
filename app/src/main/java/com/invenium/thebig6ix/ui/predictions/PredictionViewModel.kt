package com.invenium.thebig6ix.ui.predictions

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PredictionViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val predictionsCollection = db.collection("predictions")

    private val _fixtures = MutableStateFlow<List<FootballFixture>>(emptyList())
    val fixtures: StateFlow<List<FootballFixture>> = _fixtures

    // Function to retrieve the fixture data
    fun retrieveFixtureData() {
        db.collection("fixtures")
            .orderBy("date")
            .get()
            .addOnSuccessListener { documents ->
                val fixtureList = documents.mapNotNull { doc ->
                    val id = doc.getLong("id")?.toInt() ?: return@mapNotNull null
                    val home = doc.getString("homeTeam") ?: return@mapNotNull null
                    val away = doc.getString("awayTeam") ?: return@mapNotNull null
                    val date = doc.getString("date") ?: return@mapNotNull null
                    FootballFixture(id.toString(), home, away, winner = "", date = date)
                }
                _fixtures.value = fixtureList
            }
            .addOnFailureListener {
                Log.e("PredictionVM", "Error retrieving fixtures", it)
            }
    }
    fun updatePointsForCompletedFixtures() {
        FirebaseFirestore.getInstance().collection("fixtures")
            .whereGreaterThanOrEqualTo("homeTeamGoals", 0)
            .whereGreaterThanOrEqualTo("awayTeamGoals", 0)
            .get()
            .addOnSuccessListener { fixtureSnapshots ->
                for (fixtureDoc in fixtureSnapshots.documents) {
                    val fixtureId = fixtureDoc.id
                    val homeGoals = fixtureDoc.get("homeTeamGoals") as? Number
                    val awayGoals = fixtureDoc.get("awayTeamGoals") as? Number

                    // Skip the fixture if either goal field is not a valid number or if goals are -1
                    if (homeGoals == null || awayGoals == null || homeGoals.toInt() == -1 || awayGoals.toInt() == -1) {
                        Log.e("PredictionVM", "Skipping fixture $fixtureId due to invalid or missing goal data")
                        continue
                    }

                    // Continue if valid numbers for home and away goals
                    val homeGoalsInt = homeGoals.toInt()
                    val awayGoalsInt = awayGoals.toInt()

                    FirebaseFirestore.getInstance().collection("predictions")
                        .whereEqualTo("fixtureId", fixtureId)
                        .get()
                        .addOnSuccessListener { predictionSnapshots ->
                            for (predictionDoc in predictionSnapshots.documents) {
                                val prediction = predictionDoc.data ?: continue
                                val predictedHomeGoals = prediction["homeTeamGoals"] as? Int ?: continue
                                val predictedAwayGoals = prediction["awayTeamGoals"] as? Int ?: continue
                                val scoredAlready = prediction["scoredPoints"] as? Boolean ?: false
                                if (scoredAlready) continue // Skip already-scored

                                val (scoredPoints, additionalPoints) = calculatePoints(
                                    predictedHomeGoals,
                                    predictedAwayGoals,
                                    homeGoalsInt,
                                    awayGoalsInt
                                )

                                val updates = hashMapOf<String, Any>(
                                    "scoredPoints" to scoredPoints
                                )

                                if (scoredPoints) {
                                    val currentTotal = prediction["totalScore"] as? Int ?: 0
                                    updates["totalScore"] = currentTotal + additionalPoints
                                }

                                FirebaseFirestore.getInstance()
                                    .collection("predictions")
                                    .document(predictionDoc.id)
                                    .update(updates)
                            }
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PredictionVM", "Error updating points", exception)
            }
    }

    // Function to submit a prediction
    fun submitPrediction(
        homeGoals: Int,
        awayGoals: Int,
        selectedFixture: FootballFixture,
        userId: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        // Check if the user has already predicted this fixture
        predictionsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("fixtureId", selectedFixture.id)
            .get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    // If prediction exists, fetch it
                    val predictionDoc = existing.documents.first()
                    val prediction = predictionDoc.data

                    val predictedHomeGoals =
                        prediction?.get("homeTeamGoals") as? Int ?: return@addOnSuccessListener
                    val predictedAwayGoals =
                        prediction?.get("awayTeamGoals") as? Int ?: return@addOnSuccessListener

                    val actualHomeGoals = selectedFixture.homeTeamGoals
                    val actualAwayGoals = selectedFixture.awayTeamGoals

                    // Compare predicted goals with actual goals and calculate points
                    val (scoredPoints, additionalPoints) = calculatePoints(predictedHomeGoals, predictedAwayGoals, actualHomeGoals, actualAwayGoals)

                    // Prepare the fields to update
                    val updatedFields = hashMapOf<String, Any>(
                        "scoredPoints" to scoredPoints
                    )

                    if (scoredPoints) {
                        // Award points for the correct prediction
                        updatedFields["totalScore"] = (prediction["totalScore"] as? Int ?: 0) + additionalPoints
                    }

                    // Update the prediction document in Firestore
                    predictionsCollection.document(predictionDoc.id)
                        .update(updatedFields)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onFailure(it) }

                } else {
                    onFailure(Exception("No existing prediction found"))
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Function to calculate points based on the prediction
    private fun calculatePoints(
        predictedHomeGoals: Int,
        predictedAwayGoals: Int,
        actualHomeGoals: Int,
        actualAwayGoals: Int
    ): Pair<Boolean, Int> {
        var scoredPoints = false
        var additionalPoints = 0

        // Check for perfect score (correct team and exact score)
        if (predictedHomeGoals == actualHomeGoals && predictedAwayGoals == actualAwayGoals) {
            scoredPoints = true
            additionalPoints = 3 // Award 3 points for exact match
        }
        // Check for correct team prediction but not exact score (correct winner or draw)
        else if ((predictedHomeGoals > predictedAwayGoals && actualHomeGoals > actualAwayGoals) ||
            (predictedHomeGoals < predictedAwayGoals && actualHomeGoals < actualAwayGoals) ||
            (predictedHomeGoals == predictedAwayGoals && actualHomeGoals == actualAwayGoals)) {
            scoredPoints = true
            additionalPoints = 1 // Award 1 point for correct winner/draw prediction
        }

        return Pair(scoredPoints, additionalPoints)
    }
}