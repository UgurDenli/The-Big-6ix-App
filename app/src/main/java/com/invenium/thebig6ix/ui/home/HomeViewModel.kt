package com.invenium.thebig6ix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await



class HomeViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _leaderboardUsers = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboardUsers: StateFlow<List<LeaderboardUser>> = _leaderboardUsers

    private val _fixtures = MutableStateFlow<List<com.invenium.thebig6ix.data.FootballFixture>>(emptyList())
    val fixtures: StateFlow<List<com.invenium.thebig6ix.data.FootballFixture>> = _fixtures
    data class LeaderboardUser(
        val name: String,
        val weeklyScore: Int,
        val monthlyScore: Int,
        val totalScore: Int,
        val isNewLeader: Boolean = false
    )

    private val _leaderboard = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardUser>> = _leaderboard

    fun refreshFixtures(){
        fetchFixtures()
    }

    private fun fetchLeaderboard() {
        viewModelScope.launch {
            val result = db.collection("users")
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val users = result.documents.mapIndexed { index, doc ->
                LeaderboardUser(
                    name = doc.getString("fullName") ?: "Anonymous",
                    weeklyScore = (doc["weeklyScore"] as? Long)?.toInt() ?: 0,
                    monthlyScore = (doc["monthlyScore"] as? Long)?.toInt() ?: 0,
                    totalScore = (doc["score"] as? Long)?.toInt() ?: 0,
                    isNewLeader = index == 0
                )
            }
            _leaderboard.value = users
        }
    }

    init {
        fetchFixtures()
        fetchLeaderboard()
    }

    private fun fetchFixtures() {
        viewModelScope.launch {
            db.collection("fixtures").get(Source.SERVER)
                .addOnSuccessListener { result ->
                val parsedFixtures = result.mapNotNull { doc ->
                    val homeTeam = doc.getString("homeTeam") ?: return@mapNotNull null
                    val awayTeam = doc.getString("awayTeam") ?: return@mapNotNull null
                    val date = doc.getString("date") ?: ""
                    val homeGoals = doc.getLong("homeTeamGoals")?.toInt() ?: -1
                    val awayGoals = doc.getLong("awayTeamGoals")?.toInt() ?: -1
                    val winner = doc.getString("winner") ?: ""
                    val deadline = doc.getTimestamp("deadline")

                    com.invenium.thebig6ix.data.FootballFixture(
                        id = doc.id,
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        date = date,
                        homeTeamGoals = homeGoals,
                        awayTeamGoals = awayGoals,
                        winner = winner,
                        deadline = deadline
                    )
                }
                _fixtures.value = parsedFixtures
            }
        }
    }
}
