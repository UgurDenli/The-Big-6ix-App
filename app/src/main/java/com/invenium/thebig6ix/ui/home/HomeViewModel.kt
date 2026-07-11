package com.invenium.thebig6ix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

data class GwWinner(
    val uid: String = "",
    val name: String = "",
    val gwPoints: Int = 0,
    val gameweek: Int = 0,
    val profileImageUrl: String? = null
)

data class LeaderboardUser(
    val uid: String = "",
    val name: String = "",
    val totalScore: Int = 0,
    val profileImageUrl: String? = null,
    val isNewLeader: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = true,
    // Current user
    val userName: String = "",
    val userRank: Int = 0,
    val userTotalPoints: Int = 0,
    val userProfileImageUrl: String? = null,
    // Next gameweek
    val nextGwNumber: Int? = null,
    val nextGwFixtureCount: Int = 0,
    val nextGwPredicted: Int = 0,
    val nextDeadlineMs: Long? = null,
    // Leaderboard preview (top 3)
    val topUsers: List<LeaderboardUser> = emptyList()
)

class HomeViewModel : ViewModel() {
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _leaderboard = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardUser>> = _leaderboard

    private val _gwWinner = MutableStateFlow<GwWinner?>(null)
    val gwWinner: StateFlow<GwWinner?> = _gwWinner

    private var listenerRegistration: ListenerRegistration? = null
    private var gwWinnerListener: ListenerRegistration? = null
    private var previousTopUid: String? = null

    init {
        startRealtimeListener()
        loadUserStats()
        loadNextGw()
        startGwWinnerListener()
    }

    // ── Public refresh (called by pull-to-refresh) ────────────────────────────

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadUserStats()
        loadNextGw()
    }

    // ── User stats + accurate rank ────────────────────────────────────────────

    private fun loadUserStats() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc    = db.collection("users").document(uid).get().await()
                val name   = doc.getString("fullName") ?: auth.currentUser?.displayName ?: "Player"
                val points = doc.getLong("score")?.toInt() ?: 0
                val imgUrl = doc.getString("profileImageUrl")
                // ✅ Accurate global rank via count query (works for any position)
                val higherCount = try {
                    db.collection("users")
                        .whereGreaterThan("score", points)
                        .get().await()
                        .documents.size
                } catch (_: Exception) { 0 }

                _uiState.value = _uiState.value.copy(
                    userName            = name,
                    userTotalPoints     = points,
                    userProfileImageUrl = imgUrl,
                    userRank            = higherCount + 1
                )
            } catch (_: Exception) {}
        }
    }

    // ── Next GW (deadline + prediction count) ────────────────────────────────

    private fun loadNextGw() {
        val uid = auth.currentUser?.uid ?: run {
            viewModelScope.launch { _uiState.value = _uiState.value.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val now      = Date()
                val fixtures = db.collection("fixtures").get().await()
                val upcoming = fixtures.documents.filter { doc ->
                    val deadline = doc.getTimestamp("deadline")?.toDate()
                    deadline != null && deadline.after(now)
                }
                if (upcoming.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                val nearestGw    = upcoming.mapNotNull { it.getLong("gameweek")?.toInt() }.minOrNull()
                val gwFixtures   = upcoming.filter { it.getLong("gameweek")?.toInt() == nearestGw }
                val earliest     = gwFixtures.mapNotNull { it.getTimestamp("deadline")?.toDate() }.minOrNull()
                val gwFixtureIds = gwFixtures.map { it.id }.toSet()

                val preds = db.collection("predictions")
                    .whereEqualTo("userId", uid)
                    .get().await()
                val predictedIds   = preds.documents.mapNotNull { it.getString("fixtureId") }.toSet()
                val predictedCount = gwFixtureIds.intersect(predictedIds).size

                _uiState.value = _uiState.value.copy(
                    isLoading          = false,
                    nextGwNumber       = nearestGw,
                    nextGwFixtureCount = gwFixtures.size,
                    nextGwPredicted    = predictedCount,
                    nextDeadlineMs     = earliest?.time
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // ── Real-time leaderboard (top 10) ────────────────────────────────────────

    private fun startRealtimeListener() {
        val uid = auth.currentUser?.uid
        listenerRegistration = db.collection("users")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val newTopUid     = snapshot.documents.firstOrNull()?.id
                val leaderChanged = previousTopUid != null && newTopUid != previousTopUid
                previousTopUid    = newTopUid

                val users = snapshot.documents.mapIndexed { index, doc ->
                    LeaderboardUser(
                        uid             = doc.id,
                        name            = doc.getString("fullName") ?: "Anonymous",
                        totalScore      = doc.getLong("score")?.toInt() ?: 0,
                        profileImageUrl = doc.getString("profileImageUrl"),
                        isNewLeader     = index == 0 && leaderChanged
                    )
                }
                _leaderboard.value = users

                // ✅ Only update rank from listener if user IS in top 10
                val idx = users.indexOfFirst { it.uid == uid }
                if (idx >= 0) {
                    _uiState.value = _uiState.value.copy(topUsers = users.take(3), userRank = idx + 1)
                } else {
                    // User not in top 10 — preserve accurate rank set by loadUserStats()
                    _uiState.value = _uiState.value.copy(topUsers = users.take(3))
                }
            }
    }

    // ── Latest GW winner listener ─────────────────────────────────────────────

    private fun startGwWinnerListener() {
        gwWinnerListener = db.collection("gameweekWinners")
            .orderBy("gameweek", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                val uid      = doc.getString("userId") ?: return@addSnapshotListener
                val gameweek = doc.getLong("gameweek")?.toInt() ?: return@addSnapshotListener
                val gwPoints = doc.getLong("points")?.toInt() ?: 0
                viewModelScope.launch {
                    try {
                        val userDoc = db.collection("users").document(uid).get().await()
                        _gwWinner.value = GwWinner(
                            uid             = uid,
                            name            = userDoc.getString("fullName") ?: "Unknown",
                            gwPoints        = gwPoints,
                            gameweek        = gameweek,
                            profileImageUrl = userDoc.getString("profileImageUrl")
                        )
                    } catch (_: Exception) {
                        _gwWinner.value = GwWinner(uid = uid, gameweek = gameweek, gwPoints = gwPoints)
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        gwWinnerListener?.remove()
    }
}
