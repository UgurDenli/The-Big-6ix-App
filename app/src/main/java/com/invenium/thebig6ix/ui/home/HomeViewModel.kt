package com.invenium.thebig6ix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await



class HomeViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _fixtures = MutableStateFlow<List<FootballFixture>>(emptyList())
    val fixtures: StateFlow<List<FootballFixture>> = _fixtures

    init {
        fetchFixtures()
    }

    private fun fetchFixtures() {
        viewModelScope.launch {
            db.collection("fixtures").get().addOnSuccessListener { result ->
                val parsedFixtures = result.mapNotNull { doc ->
                    val homeTeam = doc.getString("homeTeam") ?: return@mapNotNull null
                    val awayTeam = doc.getString("awayTeam") ?: return@mapNotNull null
                    val date = doc.getString("date") ?: ""
                    val homeGoals = doc.getLong("homeTeamGoals")?.toInt() ?: -1
                    val awayGoals = doc.getLong("awayTeamGoals")?.toInt() ?: -1
                    val winner = doc.getString("winner") ?: ""
                    val deadline = doc.getTimestamp("deadline")

                    FootballFixture(
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
