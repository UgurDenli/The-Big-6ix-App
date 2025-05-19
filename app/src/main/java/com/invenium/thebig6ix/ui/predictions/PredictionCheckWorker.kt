package com.invenium.thebig6ix.ui.predictions

import android.content.Context
import android.gesture.Prediction
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PredictionCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()

    override fun doWork(): Result {
        return try {
            // Use runBlocking to make async calls inside a Worker, because Worker is not a suspend function
            runBlocking {
                // Fetch all predictions
                val predictionsSnapshot = withContext(Dispatchers.IO) {
                    db.collection("predictions").get().await()
                }

                predictionsSnapshot.forEach { predictionDoc ->
                    val prediction = predictionDoc.toObject(FootballFixture::class.java)
                    val fixtureId = prediction.id
                    val predictedHomeGoals = prediction.homeTeamGoals
                    val predictedAwayGoals = prediction.awayTeamGoals

                    // Fetch the fixture for comparison
                    val fixtureDoc = withContext(Dispatchers.IO) {
                        db.collection("fixtures").document(fixtureId).get().await()
                    }
                    val fixture = fixtureDoc.toObject(FootballFixture::class.java)

                    if (fixture != null) {
                        val actualHomeGoals = fixture.homeTeamGoals
                        val actualAwayGoals = fixture.awayTeamGoals

                        // Check the prediction score and update points
                        var points = 0
                        val correctTeamPrediction =
                            (predictedHomeGoals == actualHomeGoals && predictedAwayGoals == actualAwayGoals)
                        if (correctTeamPrediction) {
                            points = 3  // Perfect score (correct team and exact score)
                        } else if ((predictedHomeGoals > predictedAwayGoals && actualHomeGoals > actualAwayGoals) ||
                            (predictedHomeGoals < predictedAwayGoals && actualHomeGoals < actualAwayGoals) ||
                            (predictedHomeGoals == predictedAwayGoals && actualHomeGoals == actualAwayGoals)
                        ) {
                            points = 1  // Correct team prediction but not the exact score
                        }

                        // Update the prediction document with the points and mark scoredPoints as true
                        val updatedPrediction = mapOf(
                            "scoredPoints" to true,
                            "points" to points
                        )

                        withContext(Dispatchers.IO) {
                            db.collection("predictions")
                                .document(predictionDoc.id)
                                .update(updatedPrediction)
                                .await()
                        }

                        Log.d(
                            "PredictionCheckWorker",
                            "Prediction for fixture $fixtureId updated with $points points"
                        )
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("PredictionCheckWorker", "Error checking predictions: ${e.message}")
            Result.failure()
        }
    }
}