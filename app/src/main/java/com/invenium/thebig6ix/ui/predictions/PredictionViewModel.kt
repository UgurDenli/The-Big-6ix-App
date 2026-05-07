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
import java.util.Date

class PredictionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _allFixtures = MutableStateFlow<List<FootballFixture>>(emptyList())

    private val _fixtures = MutableStateFlow<List<FootballFixture>>(emptyList())
    val fixtures: StateFlow<List<FootballFixture>> = _fixtures

    private val _userPredictions = MutableStateFlow<List<UserPrediction>>(emptyList())
    val userPredictions: StateFlow<List<UserPrediction>> = _userPredictions

    private val _mostPickedScoreline = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())
    val mostPickedScoreline: StateFlow<Map<String, Pair<Int, Int>>> = _mostPickedScoreline

    private val _selectedGameweek = MutableStateFlow<Int?>(null)
    val selectedGameweek: StateFlow<Int?> = _selectedGameweek

    private val _availableGameweeks = MutableStateFlow<List<Int>>(emptyList())
    val availableGameweeks: StateFlow<List<Int>> = _availableGameweeks

    private val _adminGameweekOverride = MutableStateFlow<Int?>(null)
    val adminGameweekOverride: StateFlow<Int?> = _adminGameweekOverride

    private val _wildcardAvailable = MutableStateFlow(false)
    val wildcardAvailable: StateFlow<Boolean> = _wildcardAvailable

    private val _doubleDownAvailable = MutableStateFlow(false)
    val doubleDownAvailable: StateFlow<Boolean> = _doubleDownAvailable

    private val _captainAvailable = MutableStateFlow(false)
    val captainAvailable: StateFlow<Boolean> = _captainAvailable

    val isAdmin: Boolean
        get() = auth.currentUser?.email == "ugurdenli30@gmail.com"

    init {
        fetchFixtures(forceRefresh = true)
        fetchUserPredictions(forceRefresh = true)
        fetchTokenStatus()
    }

    private fun fetchTokenStatus() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                _wildcardAvailable.value = doc.getBoolean("wildcardAvailable") ?: false
                _doubleDownAvailable.value = doc.getBoolean("doubleDownAvailable") ?: false
                _captainAvailable.value = doc.getBoolean("captainAvailable") ?: false
            }
    }

    fun submitWildcard(
        fixtureId: String,
        homeGoals: Int,
        awayGoals: Int,
        gameWeek: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure("User not logged in")
        viewModelScope.launch {
            try {
                val existing = db.collection("predictions")
                    .whereEqualTo("fixtureId", fixtureId)
                    .whereEqualTo("userId", userId)
                    .get(Source.SERVER)
                    .await()

                if (existing.isEmpty) {
                    onFailure("No prediction found to wildcard.")
                    return@launch
                }

                existing.documents.first().reference.update(mapOf(
                    "homeTeamGoals" to homeGoals,
                    "awayTeamGoals" to awayGoals,
                    "wildcardUsed" to true
                )).await()

                db.collection("users").document(userId)
                    .update("wildcardAvailable", false)
                    .await()

                _wildcardAvailable.value = false
                fetchUserPredictions(forceRefresh = true)
                onSuccess()
            } catch (e: Exception) {
                onFailure("Error: ${e.message}")
            }
        }
    }

    fun fetchFixtures(forceRefresh: Boolean = false) {
        val source = if (forceRefresh) Source.SERVER else Source.DEFAULT
        db.collection("fixtures")
            .get(source)
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { doc ->
                    try {
                        FootballFixture(
                            id = doc.id,
                            homeTeam = doc.getString("homeTeam") ?: return@mapNotNull null,
                            awayTeam = doc.getString("awayTeam") ?: return@mapNotNull null,
                            date = doc.getString("date") ?: "",
                            homeTeamGoals = doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                            awayTeamGoals = doc.getLong("awayTeamGoals")?.toInt() ?: -1,
                            winner = doc.getString("winner") ?: "",
                            deadline = doc.getTimestamp("deadline"),
                            gameweek = doc.getLong("gameweek")?.toInt() ?: 0
                        )
                    } catch (e: Exception) { null }
                }
                _allFixtures.value = list
                updateAvailableGameweeks(list)
            }
    }

    private fun updateAvailableGameweeks(fixtures: List<FootballFixture>) {
        val now = Date()
        val upcoming = fixtures
            .filter { it.deadline?.toDate()?.after(now) == true }
            .map { it.gameweek }
            .distinct()
            .sorted()

        _availableGameweeks.value = upcoming

        val override = _adminGameweekOverride.value
        val target = override
            ?: upcoming.firstOrNull()
            ?: fixtures.map { it.gameweek }.distinct().maxOrNull()
            ?: 0
        selectGameweek(target)
    }

    fun selectGameweek(gameweek: Int) {
        _selectedGameweek.value = gameweek
        _fixtures.value = _allFixtures.value.filter { it.gameweek == gameweek }
    }

    fun setAdminGameweekOverride(gameweek: Int?) {
        _adminGameweekOverride.value = gameweek
        if (gameweek != null) selectGameweek(gameweek)
        else updateAvailableGameweeks(_allFixtures.value)
    }

    fun snapToNext() {
        val now = Date()
        val next = _allFixtures.value
            .filter { it.deadline?.toDate()?.after(now) == true }
            .minByOrNull { it.deadline!!.toDate() }
            ?.gameweek
        if (next != null) setAdminGameweekOverride(next)
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
        gameWeek: Int,
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
                    UserPrediction(
                        fixtureId,
                        home,
                        away,
                        doc.getBoolean("wildcardUsed") ?: false,
                        doc.getBoolean("captainUsed") ?: false
                    )
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
        val awayGoals: Int,
        val wildcardUsed: Boolean = false,
        val captainUsed: Boolean = false
    )

    fun setCaptain(
        fixtureId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure("User not logged in")
        viewModelScope.launch {
            try {
                val existing = db.collection("predictions")
                    .whereEqualTo("fixtureId", fixtureId)
                    .whereEqualTo("userId", userId)
                    .get(Source.SERVER)
                    .await()

                if (existing.isEmpty) {
                    onFailure("Submit your prediction first, then set your captain.")
                    return@launch
                }

                existing.documents.first().reference.update("captainUsed", true).await()
                db.collection("users").document(userId).update("captainAvailable", false).await()

                _captainAvailable.value = false
                fetchUserPredictions(forceRefresh = true)
                onSuccess()
            } catch (e: Exception) {
                onFailure("Error: ${e.message}")
            }
        }
    }
}
