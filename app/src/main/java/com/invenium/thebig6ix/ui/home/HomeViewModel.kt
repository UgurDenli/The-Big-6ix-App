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
    // Last GW summary
    val lastGwNumber: Int? = null,
    val lastGwUserPoints: Int = 0,
    val gwWinnerName: String? = null,
    val gwWinnerUid: String? = null,
    val gwWinnerImageUrl: String? = null,
    // Leaderboard preview (top 3)
    val topUsers: List<LeaderboardUser> = emptyList()
)

class HomeViewModel : ViewModel() {
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Keep backward-compat fields used by other composables
    private val _leaderboard = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardUser>> = _leaderboard

    private val _latestGwWinnerUid = MutableStateFlow<String?>(null)
    val latestGwWinnerUid: StateFlow<String?> = _latestGwWinnerUid

    private val _latestGwNumber = MutableStateFlow<Int?>(null)
    val latestGwNumber: StateFlow<Int?> = _latestGwNumber

    private var listenerRegistration: ListenerRegistration? = null
    private var gwWinnerListener: ListenerRegistration? = null
    private var previousTopUid: String? = null

    init {
        startRealtimeListener()
        startGwWinnerListener()
        loadUserStats()
        loadNextGw()
    }

    private fun loadUserStats() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                val name   = doc.getString("fullName") ?: auth.currentUser?.displayName ?: "Player"
                val points = doc.getLong("score")?.toInt() ?: 0
                val imgUrl = doc.getString("profileImageUrl")
                _uiState.value = _uiState.value.copy(
                    userName = name,
                    userTotalPoints = points,
                    userProfileImageUrl = imgUrl
                )
            } catch (_: Exception) {}
        }
    }

    private fun loadNextGw() {
        val uid = auth.currentUser?.uid ?: return
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

                // Find the nearest gameweek
                val nearestGw = upcoming.mapNotNull { it.getLong("gameweek")?.toInt() }.minOrNull()
                val gwFixtures = upcoming.filter { it.getLong("gameweek")?.toInt() == nearestGw }
                val earliestDeadline = gwFixtures.mapNotNull { it.getTimestamp("deadline")?.toDate() }.minOrNull()
                val gwFixtureIds = gwFixtures.map { it.id }.toSet()

                // How many of this GW has the user predicted?
                val preds = db.collection("predictions")
                    .whereEqualTo("userId", uid)
                    .get().await()
                val predictedIds = preds.documents.mapNotNull { it.getString("fixtureId") }.toSet()
                val predictedCount = gwFixtureIds.intersect(predictedIds).size

                // Last completed GW — look at predictions with points
                val lastGwPredictions = preds.documents.filter { doc ->
                    val gwDoc = fixtures.documents.firstOrNull { f -> f.id == doc.getString("fixtureId") }
                    val gw = gwDoc?.getLong("gameweek")?.toInt() ?: 0
                    gw != nearestGw && doc.getLong("points") != null
                }
                val lastGwNum = lastGwPredictions.mapNotNull { doc ->
                    fixtures.documents.firstOrNull { f -> f.id == doc.getString("fixtureId") }
                        ?.getLong("gameweek")?.toInt()
                }.maxOrNull()
                val lastGwPts = if (lastGwNum != null) {
                    lastGwPredictions.filter { doc ->
                        fixtures.documents.firstOrNull { f -> f.id == doc.getString("fixtureId") }
                            ?.getLong("gameweek")?.toInt() == lastGwNum
                    }.sumOf { it.getLong("points")?.toInt() ?: 0 }
                } else 0

                _uiState.value = _uiState.value.copy(
                    isLoading        = false,
                    nextGwNumber     = nearestGw,
                    nextGwFixtureCount = gwFixtures.size,
                    nextGwPredicted  = predictedCount,
                    nextDeadlineMs   = earliestDeadline?.time,
                    lastGwNumber     = lastGwNum,
                    lastGwUserPoints = lastGwPts
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun startGwWinnerListener() {
        gwWinnerListener = db.collection("gameweekWinners")
            .orderBy("gameweek", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                val winnerUid  = doc.getString("userId")
                val winnerName = doc.getString("userName")
                val winnerImg  = doc.getString("userImageUrl")
                val gwNum      = doc.getLong("gameweek")?.toInt()
                _latestGwWinnerUid.value = winnerUid
                _latestGwNumber.value    = gwNum
                _uiState.value = _uiState.value.copy(
                    lastGwNumber    = gwNum,
                    gwWinnerName    = winnerName,
                    gwWinnerUid     = winnerUid,
                    gwWinnerImageUrl = winnerImg
                )
            }
    }

    private fun startRealtimeListener() {
        val uid = auth.currentUser?.uid
        listenerRegistration = db.collection("users")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val newTopUid    = snapshot.documents.firstOrNull()?.id
                val leaderChanged = previousTopUid != null && newTopUid != previousTopUid
                previousTopUid = newTopUid

                val users = snapshot.documents.mapIndexed { index, doc ->
                    LeaderboardUser(
                        uid            = doc.id,
                        name           = doc.getString("fullName") ?: "Anonymous",
                        totalScore     = doc.getLong("score")?.toInt() ?: 0,
                        profileImageUrl = doc.getString("profileImageUrl"),
                        isNewLeader    = index == 0 && leaderChanged
                    )
                }
                _leaderboard.value = users

                // Compute current user rank
                val rank = users.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }?.plus(1) ?: 0
                _uiState.value = _uiState.value.copy(
                    topUsers  = users.take(3),
                    userRank  = rank
                )
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
        gwWinnerListener?.remove()
    }
}
