package com.invenium.thebig6ix.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileStats(
    val isLoading: Boolean = true,
    val totalPredictions: Int = 0,
    val correctScores: Int = 0,
    val correctResults: Int = 0,
    val accuracyPercent: Int = 0,
    val resultAccuracyPercent: Int = 0,
    val currentStreak: Int = 0,
    val gwPointsList: List<Pair<Int, Int>> = emptyList()
)

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats

    init {
        viewModelScope.launch { loadStats() }
    }

    private suspend fun loadStats() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _stats.value = ProfileStats(isLoading = false)
            return
        }

        try {
            // 1. Fetch all predictions for this user
            val predDocs = db.collection("predictions")
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .documents

            if (predDocs.isEmpty()) {
                _stats.value = ProfileStats(isLoading = false)
                return
            }

            // 2. Collect unique fixture IDs
            val fixtureIds = predDocs.mapNotNull { it.getString("fixtureId") }.distinct()

            // 3. Fetch fixtures in chunks of 30
            data class FixtureInfo(val actualHome: Int, val actualAway: Int, val gameweek: Int)

            val fixtureMap = mutableMapOf<String, FixtureInfo>()
            fixtureIds.chunked(30).forEach { chunk ->
                db.collection("fixtures")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                    .documents
                    .forEach { doc ->
                        val actualHome = doc.getLong("actualHome")?.toInt() ?: -1
                        val actualAway = doc.getLong("actualAway")?.toInt() ?: -1
                        val gameweek   = doc.getLong("gameweek")?.toInt()   ?: 0
                        fixtureMap[doc.id] = FixtureInfo(actualHome, actualAway, gameweek)
                    }
            }

            // 4. Evaluate each prediction
            data class PredResult(
                val gameweek: Int,
                val isScored: Boolean,
                val correctScore: Boolean,
                val correctResult: Boolean,
                val points: Int
            )

            val results = predDocs.map { doc ->
                val fixtureId    = doc.getString("fixtureId") ?: ""
                val fixture      = fixtureMap[fixtureId]
                val predictedHome = doc.getLong("predictedHome")?.toInt() ?: -1
                val predictedAway = doc.getLong("predictedAway")?.toInt() ?: -1
                val points        = doc.getLong("points")?.toInt() ?: 0

                if (fixture == null) {
                    PredResult(0, false, false, false, points)
                } else {
                    val actualHome = fixture.actualHome
                    val actualAway = fixture.actualAway
                    val isScored   = actualHome >= 0 && actualAway >= 0

                    val correctScore = isScored &&
                            predictedHome == actualHome &&
                            predictedAway == actualAway

                    val correctResult = isScored && run {
                        val actualDir    = actualHome.compareTo(actualAway)
                        val predictedDir = predictedHome.compareTo(predictedAway)
                        // same sign: both home win, both away win, or both draw
                        (actualDir > 0 && predictedDir > 0) ||
                        (actualDir < 0 && predictedDir < 0) ||
                        (actualDir == 0 && predictedDir == 0)
                    }

                    PredResult(fixture.gameweek, isScored, correctScore, correctResult, points)
                }
            }

            // 5. Aggregate totals (only scored predictions)
            val scored        = results.filter { it.isScored }
            val totalPreds    = scored.size
            val correctScores = scored.count { it.correctScore }
            val correctResults = scored.count { it.correctResult }

            val accuracyPercent       = if (totalPreds > 0) (correctScores  * 100) / totalPreds else 0
            val resultAccuracyPercent = if (totalPreds > 0) (correctResults * 100) / totalPreds else 0

            // 6. gwPointsList — sum points per GW, only scored GWs, sorted ascending
            val gwPointsList = results
                .filter { it.isScored && it.gameweek > 0 }
                .groupBy { it.gameweek }
                .map { (gw, preds) -> Pair(gw, preds.sumOf { it.points }) }
                .sortedBy { it.first }

            // 7. Current streak — iterate GWs descending; streak continues while GW has >= 1 point
            val scoredGws = results
                .filter { it.isScored && it.gameweek > 0 }
                .map { it.gameweek }
                .distinct()
                .sortedDescending()

            // Group results by gameweek for point lookup
            val gwResultsMap = results.filter { it.isScored }.groupBy { it.gameweek }

            var streak = 0
            for (gw in scoredGws) {
                val gwPreds = gwResultsMap[gw] ?: break
                val earned  = gwPreds.any { it.points > 0 }
                if (earned) streak++ else break
            }

            _stats.value = ProfileStats(
                isLoading             = false,
                totalPredictions      = totalPreds,
                correctScores         = correctScores,
                correctResults        = correctResults,
                accuracyPercent       = accuracyPercent,
                resultAccuracyPercent = resultAccuracyPercent,
                currentStreak         = streak,
                gwPointsList          = gwPointsList
            )

        } catch (e: Exception) {
            _stats.value = ProfileStats(isLoading = false)
        }
    }
}
