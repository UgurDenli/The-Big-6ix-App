package com.invenium.thebig6ix.ui.leaderboard

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

class LeaderboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _communityUsers = MutableStateFlow<List<UserScore>>(emptyList())
    val communityUsers: StateFlow<List<UserScore>> = _communityUsers

    private val _panelScores = MutableStateFlow<List<PanelScore>>(emptyList())
    val panelScores: StateFlow<List<PanelScore>> = _panelScores

    val pageSize = 10
    private val _communityPage = MutableStateFlow(0)
    val communityPage: StateFlow<Int> = _communityPage

    private val _panelPage = MutableStateFlow(0)
    val panelPage: StateFlow<Int> = _panelPage

    private var communityListener: ListenerRegistration? = null
    private var panelListener: ListenerRegistration? = null

    init {
        startCommunityListener()
        startPanelListener()
    }

    private fun startCommunityListener() {
        communityListener = db.collection("users")
            .orderBy("score", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                _communityUsers.value = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("fullName") ?: return@mapNotNull null
                    UserScore(
                        uid = doc.id,
                        name = name,
                        score = doc.getLong("score")?.toInt() ?: 0,
                        profileImageUrl = doc.getString("profileImageUrl")
                    )
                }
            }
    }

    private fun startPanelListener() {
        panelListener = db.collection("config")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val panels = snapshot.documents
                    .filter { it.id.startsWith("panel") }
                    .mapNotNull { doc ->
                        val name = doc.getString("fullName") ?: return@mapNotNull null
                        PanelScore(
                            name = name,
                            score = doc.getLong("score")?.toInt() ?: 0
                        )
                    }
                    .sortedByDescending { it.score }
                _panelScores.value = panels
            }
    }

    fun setCommunityPage(page: Int) { _communityPage.value = page }
    fun setPanelPage(page: Int) { _panelPage.value = page }

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
    }
}
