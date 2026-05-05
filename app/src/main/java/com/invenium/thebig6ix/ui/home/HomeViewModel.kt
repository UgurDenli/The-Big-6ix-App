package com.invenium.thebig6ix.ui.home

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LeaderboardUser(
    val uid: String = "",
    val name: String = "",
    val totalScore: Int = 0,
    val profileImageUrl: String? = null,
    val isNewLeader: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _leaderboard = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardUser>> = _leaderboard

    private var listenerRegistration: ListenerRegistration? = null
    private var previousTopUid: String? = null

    init {
        startRealtimeListener()
    }

    private fun startRealtimeListener() {
        listenerRegistration = db.collection("users")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val newTopUid = snapshot.documents.firstOrNull()?.id
                val leaderChanged = previousTopUid != null && newTopUid != previousTopUid
                previousTopUid = newTopUid

                val users = snapshot.documents.mapIndexed { index, doc ->
                    LeaderboardUser(
                        uid = doc.id,
                        name = doc.getString("fullName") ?: "Anonymous",
                        totalScore = doc.getLong("score")?.toInt() ?: 0,
                        profileImageUrl = doc.getString("profileImageUrl"),
                        isNewLeader = index == 0 && leaderChanged
                    )
                }
                _leaderboard.value = users
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
