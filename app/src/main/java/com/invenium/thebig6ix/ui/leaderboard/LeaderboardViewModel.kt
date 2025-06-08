package com.invenium.thebig6ix.ui.leaderboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

enum class FilterType {
    WEEKLY, MONTHLY, ALL_TIME
}

data class UserScore(
    val uid: String,
    val name: String,
    val score: Int,
    val profileImageUrl: String? = null,
    val trend: Int = 0 // +1 = up, -1 = down, 0 = same or unknown
)

class LeaderboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _selectedFilter = MutableStateFlow(FilterType.ALL_TIME)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter

    private val _users = MutableStateFlow<List<UserScore>>(emptyList())
    val users: StateFlow<List<UserScore>> = _users

    private var previousUsers: List<UserScore> = emptyList()

    fun setFilter(filter: FilterType) {
        _selectedFilter.value = filter
        fetchLeaderboard()
    }

    init {
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").get().await()
                val filter = _selectedFilter.value
                Log.d("Leaderboard", "Filter selected: $filter")

                val sortedList = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    val name = doc.getString("fullName") ?: return@mapNotNull null
                    val profileImageUrl = doc.getString("profileImageUrl")
                    val score = when (filter) {
                        FilterType.ALL_TIME -> doc.getLong("score")?.toInt() ?: 0
                        FilterType.MONTHLY -> doc.getLong("monthlyScore")?.toInt() ?: 0
                        FilterType.WEEKLY -> doc.getLong("weeklyScore")?.toInt() ?: 0
                    }
                    Triple(uid, name, UserScore(uid, name, score, profileImageUrl))
                }.sortedByDescending { it.third.score }

                val userScoreList = sortedList.mapIndexed { index, (_, _, scoreData) ->
                    val doc = snapshot.documents.find { it.id == scoreData.uid }
                    val previousRanksMap = doc?.get("previousRanks") as? Map<String, Long> ?: emptyMap()
                    val prevRank = previousRanksMap[filter.name] ?: -1L
                    val currentRank = index + 1
                    val trend = when {
                        prevRank == -1L -> 0 // no data
                        currentRank < prevRank -> +1 // moved up
                        currentRank > prevRank -> -1 // moved down
                        else -> 0 // same
                    }
                    scoreData.copy(trend = trend)
                }

                _users.value = userScoreList

            } catch (e: Exception) {
                Log.e("Leaderboard", "Error fetching users", e)
            }
        }
    }
}
