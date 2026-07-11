package com.invenium.thebig6ix.ui.predictions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    // Which gameweek Double Down is locked in for (null if unused). Drives the
    // "ACTIVE THIS GW" badge so users can see where their 2× is applied.
    private val _doubleDownUsedGameweek = MutableStateFlow<Int?>(null)
    val doubleDownUsedGameweek: StateFlow<Int?> = _doubleDownUsedGameweek

    private val _captainAvailable = MutableStateFlow(false)
    val captainAvailable: StateFlow<Boolean> = _captainAvailable

    private val _isAdmin = MutableStateFlow(false)
    val isAdminFlow: StateFlow<Boolean> = _isAdmin

    // Expose as a plain val for places that read it synchronously
    val isAdmin: Boolean get() = _isAdmin.value

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var fixturesListener: ListenerRegistration? = null

    init {
        startFixturesListener()
        fetchUserPredictions(forceRefresh = true)
        fetchTokenStatus()
        checkAdminStatus()
    }

    private fun buildFixtureList(documents: List<DocumentSnapshot>): List<FootballFixture> {
        val list = documents.mapNotNull { doc ->
            try {
                val homeTeam = doc.getString("homeTeam") ?: run {
                    Log.w("PredictionVM", "Skipping ${doc.id} — missing homeTeam")
                    return@mapNotNull null
                }
                val awayTeam = doc.getString("awayTeam") ?: run {
                    Log.w("PredictionVM", "Skipping ${doc.id} — missing awayTeam")
                    return@mapNotNull null
                }
                FootballFixture(
                    id            = doc.id,
                    homeTeam      = homeTeam,
                    awayTeam      = awayTeam,
                    date          = doc.getString("date")       ?: "",
                    homeTeamGoals = doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                    awayTeamGoals = doc.getLong("awayTeamGoals")?.toInt() ?: -1,
                    winner        = doc.getString("winner")     ?: "",
                    deadline      = doc.getTimestamp("deadline"),
                    gameweek      = doc.getLong("gameweek")?.toInt() ?: 0
                )
            } catch (e: Exception) {
                Log.e("PredictionVM", "Parse exception for ${doc.id}: ${e.message}", e)
                null
            }
        }
        Log.d("PredictionVM", "buildFixtureList: ${list.size} parsed from ${documents.size} docs | IDs: ${list.map { it.id }}")
        return list
    }

    private fun startFixturesListener() {
        fixturesListener?.remove()

        // Explicit server fetch — bypasses stale local cache to guarantee fresh data.
        // This is the authoritative load; the snapshot listener below keeps us live after.
        _isLoading.value = true
        db.collection("fixtures")
            .get(Source.SERVER)
            .addOnSuccessListener { snap ->
                val list = buildFixtureList(snap.documents)
                Log.d("PredictionVM", "SERVER fetch: ${list.size} fixtures")
                _allFixtures.value = list
                updateAvailableGameweeks(list)
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e("PredictionVM", "SERVER fetch failed: ${e.message}")
                _isLoading.value = false
            }

        // Real-time listener keeps scores/deadlines current while the screen is open.
        // Skip cache-only snapshots to avoid overwriting fresh server data with stale cache.
        fixturesListener = db.collection("fixtures")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e("PredictionVM", "Snapshot listener error: ${error?.message}")
                    return@addSnapshotListener
                }
                if (snapshot.metadata.isFromCache) {
                    Log.d("PredictionVM", "Snapshot from cache (${snapshot.size()} docs) — skipping, waiting for server")
                    return@addSnapshotListener
                }
                val list = buildFixtureList(snapshot.documents)
                Log.d("PredictionVM", "Snapshot from SERVER: ${list.size} fixtures")
                _allFixtures.value = list
                updateAvailableGameweeks(list)
                _isLoading.value = false
            }
    }

    override fun onCleared() {
        super.onCleared()
        fixturesListener?.remove()
    }

    private fun checkAdminStatus() {
        val user = auth.currentUser ?: return
        if (user.email == "ugurdenli30@gmail.com") { _isAdmin.value = true; return }
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc -> _isAdmin.value = doc.getBoolean("isAdmin") == true }
    }

    private fun fetchTokenStatus() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                _wildcardAvailable.value = doc.getBoolean("wildcardAvailable") ?: true
                _doubleDownAvailable.value = doc.getBoolean("doubleDownAvailable") ?: true
                _captainAvailable.value = doc.getBoolean("captainAvailable") ?: true
                _doubleDownUsedGameweek.value = doc.getLong("doubleDownUsedGameweek")?.toInt()
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

    // Manual Refresh button — restarts the listener and forces a server fetch
    fun fetchFixtures(forceRefresh: Boolean = false) {
        Log.d("PredictionVM", "fetchFixtures called (forceRefresh=$forceRefresh)")
        startFixturesListener()
    }

    private fun updateAvailableGameweeks(fixtures: List<FootballFixture>) {
        val now = Date()
        val gwGroups = fixtures.groupBy { it.gameweek }

        // Current GW = the latest GW whose first game has already kicked off
        val currentGw = gwGroups.entries
            .filter { (_, gfixtures) ->
                val minDeadline = gfixtures.mapNotNull { it.deadline?.toDate() }.minOrNull()
                minDeadline != null && minDeadline.before(now)
            }
            .maxOfOrNull { it.key }

        // Next GW = the earliest GW whose first game is still in the future
        val nextGw = gwGroups.entries
            .filter { (_, gfixtures) ->
                val minDeadline = gfixtures.mapNotNull { it.deadline?.toDate() }.minOrNull()
                minDeadline != null && minDeadline.after(now)
            }
            .minOfOrNull { it.key }

        val tabs = listOfNotNull(currentGw, nextGw).distinct().sorted()
        _availableGameweeks.value = tabs

        val override = _adminGameweekOverride.value
        val target = override
            ?: nextGw
            ?: currentGw
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
                        "fixtureId"    to fixtureId,
                        "homeTeam"     to homeTeam,
                        "awayTeam"     to awayTeam,
                        "homeTeamGoals" to homeGoals,
                        "awayTeamGoals" to awayGoals,
                        "userId"       to userId,
                        "scoredPoints" to false,
                        "gameweek"     to gameWeek,
                        "submittedAt"  to FieldValue.serverTimestamp()
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

    fun submitDoubleDown(
        gameweek: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return onFailure("User not logged in")
        viewModelScope.launch {
            try {
                db.collection("users").document(userId)
                    .update(mapOf(
                        "doubleDownAvailable" to false,
                        "doubleDownUsedGameweek" to gameweek
                    ))
                    .await()
                _doubleDownAvailable.value = false
                _doubleDownUsedGameweek.value = gameweek
                onSuccess()
            } catch (e: Exception) {
                onFailure("Error: ${e.message}")
            }
        }
    }
}
