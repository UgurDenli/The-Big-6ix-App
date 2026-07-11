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

// ── Badge model ───────────────────────────────────────────────────────────────

data class Badge(
    val id: String,
    val emoji: String,
    val label: String,
    val description: String,
    val earned: Boolean
)

private fun computeBadges(stats: ProfileStats): List<Badge> {
    val maxGwPoints = stats.gwPointsList.maxOfOrNull { it.second } ?: 0
    val gwCount     = stats.gwPointsList.size
    return listOf(
        Badge("first_blood",  "🩸", "First Blood",   "Get your first correct score",          stats.correctScores >= 1),
        Badge("hat_trick",    "🎩", "Hat Trick",     "Score 9+ pts in a single gameweek",     maxGwPoints >= 9),
        Badge("on_fire",      "🔥", "On Fire",       "Score points 3 gameweeks in a row",     stats.currentStreak >= 3),
        Badge("veteran",      "⚔️", "Veteran",       "Participate in 5+ gameweeks",           gwCount >= 5),
        Badge("sharpshooter", "🎯", "Sharpshooter",  "Hit 50%+ correct score accuracy",       stats.accuracyPercent >= 50),
        Badge("centurion",    "💯", "Centurion",     "Score in 10+ different gameweeks",      gwCount >= 10),
        Badge("clean_sheet",  "🛡️", "Clean Sheet",   "Maintain a 5+ gameweek scoring streak", stats.currentStreak >= 5),
        Badge("analyst",      "📊", "Analyst",       "Hit 80%+ correct result accuracy",      stats.resultAccuracyPercent >= 80)
    )
}

// ── ProfileStats ──────────────────────────────────────────────────────────────

data class ProfileStats(
    val isLoading: Boolean = true,
    val totalPredictions: Int = 0,
    val correctScores: Int = 0,
    val correctResults: Int = 0,
    val accuracyPercent: Int = 0,
    val resultAccuracyPercent: Int = 0,
    val currentStreak: Int = 0,
    val gwPointsList: List<Pair<Int, Int>> = emptyList(),   // (gameweek → total pts)
    val bestGwScore: Int = 0,
    val avgPredictedGoals: Float = 0f,                      // avg goals per prediction submitted
    val avgActualGoals: Float = 0f,                         // avg goals per scored fixture
    val badges: List<Badge> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats

    init { viewModelScope.launch { loadStats() } }

    fun refresh() { viewModelScope.launch { loadStats() } }

    private suspend fun loadStats() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _stats.value = ProfileStats(isLoading = false)
            return
        }

        try {
            // ── 1. Fetch all predictions for this user ─────────────────────
            val predDocs = db.collection("predictions")
                .whereEqualTo("userId", uid)
                .get().await().documents

            if (predDocs.isEmpty()) {
                val empty = ProfileStats(isLoading = false)
                _stats.value = empty.copy(badges = computeBadges(empty))
                return
            }

            // ── 2. Collect unique fixture IDs ──────────────────────────────
            val fixtureIds = predDocs.mapNotNull { it.getString("fixtureId") }.distinct()

            // ── 3. Fetch fixtures in chunks of 30 ──────────────────────────
            data class FixtureInfo(val actualHome: Int, val actualAway: Int, val gameweek: Int)
            val fixtureMap = mutableMapOf<String, FixtureInfo>()

            fixtureIds.chunked(30).forEach { chunk ->
                db.collection("fixtures")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get().await().documents
                    .forEach { doc ->
                        fixtureMap[doc.id] = FixtureInfo(
                            actualHome = doc.getLong("homeTeamGoals")?.toInt() ?: -1, // ✅ fixed
                            actualAway = doc.getLong("awayTeamGoals")?.toInt() ?: -1, // ✅ fixed
                            gameweek   = doc.getLong("gameweek")?.toInt() ?: 0
                        )
                    }
            }

            // ── 4. Evaluate each prediction ────────────────────────────────
            data class PredResult(
                val gameweek: Int,
                val isScored: Boolean,
                val correctScore: Boolean,
                val correctResult: Boolean,
                val awardedPoints: Int
            )

            val results = predDocs.map { doc ->
                val fixtureId     = doc.getString("fixtureId") ?: ""
                val fixture       = fixtureMap[fixtureId]
                val predictedHome = doc.getLong("homeTeamGoals")?.toInt() ?: -1
                val predictedAway = doc.getLong("awayTeamGoals")?.toInt() ?: -1
                val awarded       = doc.getLong("awardedPoints")?.toInt() ?: 0
                // scoredPoints boolean is set by the admin scoring process
                val scoredBool    = doc.getBoolean("scoredPoints") == true

                val actualHome = fixture?.actualHome ?: -1
                val actualAway = fixture?.actualAway ?: -1
                val goalsKnown = actualHome >= 0 && actualAway >= 0

                // Scored if fixture has goals, admin flagged it, or points were awarded
                val isScored = goalsKnown || scoredBool || awarded > 0

                val correctScore = when {
                    awarded >= 3 -> true                 // trust stored points first
                    goalsKnown   -> predictedHome == actualHome && predictedAway == actualAway
                    else         -> false
                }
                val correctResult = when {
                    awarded >= 1 -> true                 // any points = at least correct result
                    goalsKnown   -> run {
                        val ad = actualHome.compareTo(actualAway)
                        val pd = predictedHome.compareTo(predictedAway)
                        (ad > 0 && pd > 0) || (ad < 0 && pd < 0) || (ad == 0 && pd == 0)
                    }
                    else         -> false
                }
                val points = when {
                    awarded > 0   -> awarded
                    correctScore  -> 3
                    correctResult -> 1
                    else          -> 0
                }

                val gw = fixture?.gameweek ?: doc.getLong("gameweek")?.toInt() ?: 0
                PredResult(gw, isScored, correctScore, correctResult, points)
            }

            // ── 5. Aggregate totals ────────────────────────────────────────
            val scored        = results.filter { it.isScored }
            val totalPreds    = scored.size
            val correctScores = scored.count { it.correctScore }
            val correctResults = scored.count { it.correctResult }

            val accuracyPercent       = if (totalPreds > 0) (correctScores  * 100) / totalPreds else 0
            val resultAccuracyPercent = if (totalPreds > 0) (correctResults * 100) / totalPreds else 0

            // ── 6. GW points list (sorted ascending by GW number) ──────────
            val gwPointsList = results
                .filter { it.isScored && it.gameweek > 0 }
                .groupBy { it.gameweek }
                .map { (gw, preds) -> Pair(gw, preds.sumOf { it.awardedPoints }) }
                .sortedBy { it.first }

            val bestGwScore = gwPointsList.maxOfOrNull { it.second } ?: 0

            // ── 7. Current streak ──────────────────────────────────────────
            val scoredGws    = results.filter { it.isScored && it.gameweek > 0 }.map { it.gameweek }.distinct().sortedDescending()
            val gwResultsMap = results.filter { it.isScored }.groupBy { it.gameweek }

            var streak = 0
            for (gw in scoredGws) {
                val gwPreds = gwResultsMap[gw] ?: break
                if (gwPreds.any { it.awardedPoints > 0 }) streak++ else break
            }

            // ── 8. Over/Under bias (predicted vs actual goals) ─────────────
            // Build list of (predictedHome, predictedAway, actualHome, actualAway) for scored preds
            data class GoalPair(
                val predHome: Int, val predAway: Int,
                val actualHome: Int, val actualAway: Int
            )
            val goalPairs = predDocs.mapNotNull { doc ->
                val fId      = doc.getString("fixtureId") ?: return@mapNotNull null
                val fixture  = fixtureMap[fId] ?: return@mapNotNull null
                if (fixture.actualHome < 0 || fixture.actualAway < 0) return@mapNotNull null
                GoalPair(
                    predHome   = doc.getLong("homeTeamGoals")?.toInt() ?: return@mapNotNull null,
                    predAway   = doc.getLong("awayTeamGoals")?.toInt() ?: return@mapNotNull null,
                    actualHome = fixture.actualHome,
                    actualAway = fixture.actualAway
                )
            }
            val avgPredictedGoals = if (goalPairs.isNotEmpty())
                goalPairs.map { it.predHome + it.predAway }.average().toFloat() else 0f
            val avgActualGoals = if (goalPairs.isNotEmpty())
                goalPairs.map { it.actualHome + it.actualAway }.average().toFloat() else 0f

            val partial = ProfileStats(
                isLoading             = false,
                totalPredictions      = totalPreds,
                correctScores         = correctScores,
                correctResults        = correctResults,
                accuracyPercent       = accuracyPercent,
                resultAccuracyPercent = resultAccuracyPercent,
                currentStreak         = streak,
                gwPointsList          = gwPointsList,
                bestGwScore           = bestGwScore,
                avgPredictedGoals     = avgPredictedGoals,
                avgActualGoals        = avgActualGoals
            )
            _stats.value = partial.copy(badges = computeBadges(partial))

        } catch (_: Exception) {
            _stats.value = ProfileStats(isLoading = false)
        }
    }
}
