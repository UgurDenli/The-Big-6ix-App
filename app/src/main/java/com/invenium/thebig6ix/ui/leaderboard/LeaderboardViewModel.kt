package com.invenium.thebig6ix.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserScore(
    val uid: String = "",
    val name: String = "",
    val score: Int = 0,
    val profileImageUrl: String? = null
)

data class PanelScore(
    val name: String = "",
    val score: Int = 0
)

data class SharpUser(
    val uid: String = "",
    val name: String = "",
    val correctScores: Int = 0,
    val profileImageUrl: String? = null
)

class LeaderboardViewModel : ViewModel() {
    private val db  = FirebaseFirestore.getInstance()
    val currentUserUid: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _communityUsers = MutableStateFlow<List<UserScore>>(emptyList())
    val communityUsers: StateFlow<List<UserScore>> = _communityUsers

    private val _panelScores = MutableStateFlow<List<PanelScore>>(emptyList())
    val panelScores: StateFlow<List<PanelScore>> = _panelScores

    val pageSize = 10
    private val _communityPage = MutableStateFlow(0)
    val communityPage: StateFlow<Int> = _communityPage

    private val _panelPage = MutableStateFlow(0)
    val panelPage: StateFlow<Int> = _panelPage

    private val _latestGwWinnerUid = MutableStateFlow<String?>(null)
    val latestGwWinnerUid: StateFlow<String?> = _latestGwWinnerUid

    private val _latestGwNumber = MutableStateFlow<Int?>(null)
    val latestGwNumber: StateFlow<Int?> = _latestGwNumber

    private val _sharpsUsers = MutableStateFlow<List<SharpUser>>(emptyList())
    val sharpsUsers: StateFlow<List<SharpUser>> = _sharpsUsers

    private val _sharpsLoading = MutableStateFlow(false)
    val sharpsLoading: StateFlow<Boolean> = _sharpsLoading

    private var communityListener: ListenerRegistration? = null
    private var panelListener: ListenerRegistration? = null
    private var gwWinnerListener: ListenerRegistration? = null

    init {
        startCommunityListener()
        startPanelListener()
        startGwWinnerListener()
        loadSharps()
    }

    // ── Pull-to-refresh ───────────────────────────────────────────────────────

    fun refresh() {
        _isLoading.value = true
        communityListener?.remove()
        panelListener?.remove()
        startCommunityListener()
        startPanelListener()
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun startGwWinnerListener() {
        gwWinnerListener = db.collection("gameweekWinners")
            .orderBy("gameweek", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val doc = snap?.documents?.firstOrNull() ?: return@addSnapshotListener
                _latestGwWinnerUid.value = doc.getString("userId")
                _latestGwNumber.value    = doc.getLong("gameweek")?.toInt()
            }
    }

    private fun startCommunityListener() {
        communityListener = db.collection("users")
            .orderBy("score", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                _communityUsers.value = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("fullName") ?: return@mapNotNull null
                    UserScore(
                        uid             = doc.id,
                        name            = name,
                        score           = doc.getLong("score")?.toInt() ?: 0,
                        profileImageUrl = doc.getString("profileImageUrl")
                    )
                }
                _isLoading.value = false
            }
    }

    private fun startPanelListener() {
        panelListener = db.collection("config")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                _panelScores.value = snapshot.documents
                    .filter { it.id.startsWith("panel") }
                    .mapNotNull { doc ->
                        val name = doc.getString("fullName") ?: return@mapNotNull null
                        PanelScore(name = name, score = doc.getLong("score")?.toInt() ?: 0)
                    }
                    .sortedByDescending { it.score }
            }
    }

    // ── Sharps leaderboard (correct exact scores) ─────────────────────────────

    fun loadSharps() {
        _sharpsLoading.value = true
        viewModelScope.launch {
            try {
                // All predictions where the admin awarded 3 pts (correct score)
                val preds = db.collection("predictions")
                    .whereGreaterThanOrEqualTo("awardedPoints", 3)
                    .get().await()

                val countByUid = mutableMapOf<String, Int>()
                preds.documents.forEach { doc ->
                    val uid = doc.getString("userId") ?: return@forEach
                    countByUid[uid] = (countByUid[uid] ?: 0) + 1
                }

                if (countByUid.isEmpty()) {
                    _sharpsUsers.value = emptyList()
                    return@launch
                }

                // Fetch user info in chunks
                val userMap = mutableMapOf<String, Pair<String, String?>>()
                countByUid.keys.toList().chunked(30).forEach { chunk ->
                    db.collection("users")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get().await().documents
                        .forEach { doc ->
                            userMap[doc.id] = Pair(
                                doc.getString("fullName") ?: "Anonymous",
                                doc.getString("profileImageUrl")
                            )
                        }
                }

                _sharpsUsers.value = countByUid.entries
                    .mapNotNull { (uid, count) ->
                        val (name, img) = userMap[uid] ?: return@mapNotNull null
                        SharpUser(uid = uid, name = name, correctScores = count, profileImageUrl = img)
                    }
                    .sortedByDescending { it.correctScores }
            } catch (_: Exception) {
            } finally {
                _sharpsLoading.value = false
            }
        }
    }

    // ── Pagination helpers ────────────────────────────────────────────────────

    fun currentUserRank(): Int {
        val uid = currentUserUid ?: return 0
        val idx = _communityUsers.value.indexOfFirst { it.uid == uid }
        return if (idx >= 0) idx + 1 else 0
    }

    fun currentUserScore(): UserScore? {
        val uid = currentUserUid ?: return null
        return _communityUsers.value.firstOrNull { it.uid == uid }
    }

    fun setCommunityPage(page: Int) { _communityPage.value = page }
    fun setPanelPage(page: Int)     { _panelPage.value = page }

    fun communityPageCount(): Int {
        val total = _communityUsers.value.size
        return if (total == 0) 1 else (total + pageSize - 1) / pageSize
    }

    fun panelPageCount(): Int {
        val total = _panelScores.value.size
        return if (total == 0) 1 else (total + pageSize - 1) / pageSize
    }

    override fun onCleared() {
        super.onCleared()
        communityListener?.remove()
        panelListener?.remove()
        gwWinnerListener?.remove()
    }
}
