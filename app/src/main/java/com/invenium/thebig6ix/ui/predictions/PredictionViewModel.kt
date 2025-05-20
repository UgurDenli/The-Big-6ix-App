package com.invenium.thebig6ix.ui.predictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class PredictionViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun submitPrediction(fixtureId: String, homeGoals: Int, awayGoals: Int) {
        val userId = auth.currentUser?.uid ?: return
        val prediction = mapOf(
            "fixtureId" to fixtureId,
            "homeTeamGoals" to homeGoals,
            "awayTeamGoals" to awayGoals,
            "userId" to userId,
            "scoredPoints" to false,
            "totalScore" to 0
        )

        viewModelScope.launch {
            db.collection("predictions")
                .document()
                .set(prediction)
        }
    }
    fun updatePointsForCompletedFixtures() {
        // logic to update points
    }
}
