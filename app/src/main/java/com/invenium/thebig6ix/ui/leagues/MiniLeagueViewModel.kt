package com.invenium.thebig6ix.ui.leagues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ── Data model ────────────────────────────────────────────────────────────────

data class HeadToHeadEntry(
    val fixtureId: String,
    val homeTeam: String,
    val awayTeam: String,
    val actualHome: Int,        // -1 if not yet scored
    val actualAway: Int,
    val myPredHome: Int,        // -1 if no prediction
    val myPredAway: Int,
    val opponentPredHome: Int,
    val opponentPredAway: Int,
    val myPoints: Int,
    val opponentPoints: Int,
    val gameweek: Int
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MiniLeagueViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _h2hEntries = MutableStateFlow<List<HeadToHeadEntry>>(emptyList())
    val h2hEntries: StateFlow<List<HeadToHeadEntry>> = _h2hEntries

    private val _h2hLoading = MutableStateFlow(false)
    val h2hLoading: StateFlow<Boolean> = _h2hLoading

    private val _h2hOpponentUid = MutableStateFlow("")
    val h2hOpponentUid: StateFlow<String> = _h2hOpponentUid

    private val _h2hOpponentName = MutableStateFlow("")
    val h2hOpponentName: StateFlow<String> = _h2hOpponentName

    private val _h2hGameweek = MutableStateFlow(1)
    val h2hGameweek: StateFlow<Int> = _h2hGameweek

    private val _h2hMaxGw = MutableStateFlow(1)
    val h2hMaxGw: StateFlow<Int> = _h2hMaxGw

    // ── Select opponent — finds latest GW then loads comparison ──────────────

    fun selectOpponent(uid: String, name: String) {
        _h2hOpponentUid.value  = uid
        _h2hOpponentName.value = name
        _h2hEntries.value      = emptyList()
        viewModelScope.launch {
            try {
                val snap = db.collection("fixtures")
                    .orderBy("gameweek", Query.Direction.DESCENDING)
                    .limit(1).get().await()
                val latestGw = snap.documents.firstOrNull()?.getLong("gameweek")?.toInt() ?: 1
                _h2hMaxGw.value = latestGw
                loadHeadToHead(uid, latestGw)
            } catch (_: Exception) {
                _h2hLoading.value = false
            }
        }
    }

    // ── Navigate to a different GW ────────────────────────────────────────────

    fun setH2hGw(gameweek: Int) {
        val uid = _h2hOpponentUid.value
        if (uid.isNotEmpty()) loadHeadToHead(uid, gameweek)
    }

    // ── Load comparison data ──────────────────────────────────────────────────

    fun loadHeadToHead(opponentUid: String, gameweek: Int) {
        val myUid = auth.currentUser?.uid ?: return
        _h2hGameweek.value = gameweek
        _h2hLoading.value  = true
        viewModelScope.launch {
            try {
                val fixtureSnap = db.collection("fixtures")
                    .whereEqualTo("gameweek", gameweek)
                    .get().await()

                val myPreds = db.collection("predictions")
                    .whereEqualTo("userId", myUid)
                    .whereEqualTo("gameweek", gameweek)
                    .get().await().documents
                val myMap = myPreds.associateBy { it.getString("fixtureId") ?: "" }

                val opPreds = db.collection("predictions")
                    .whereEqualTo("userId", opponentUid)
                    .whereEqualTo("gameweek", gameweek)
                    .get().await().documents
                val opMap = opPreds.associateBy { it.getString("fixtureId") ?: "" }

                _h2hEntries.value = fixtureSnap.documents.mapNotNull { doc ->
                    val fId = doc.id
                    HeadToHeadEntry(
                        fixtureId        = fId,
                        homeTeam         = doc.getString("homeTeam") ?: return@mapNotNull null,
                        awayTeam         = doc.getString("awayTeam") ?: return@mapNotNull null,
                        actualHome       = doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                        actualAway       = doc.getLong("awayTeamGoals")?.toInt() ?: -1,
                        myPredHome       = myMap[fId]?.getLong("homeTeamGoals")?.toInt() ?: -1,
                        myPredAway       = myMap[fId]?.getLong("awayTeamGoals")?.toInt() ?: -1,
                        opponentPredHome = opMap[fId]?.getLong("homeTeamGoals")?.toInt() ?: -1,
                        opponentPredAway = opMap[fId]?.getLong("awayTeamGoals")?.toInt() ?: -1,
                        myPoints         = myMap[fId]?.getLong("awardedPoints")?.toInt() ?: 0,
                        opponentPoints   = opMap[fId]?.getLong("awardedPoints")?.toInt() ?: 0,
                        gameweek         = gameweek
                    )
                }.sortedBy { it.homeTeam }
            } catch (_: Exception) {
            } finally {
                _h2hLoading.value = false
            }
        }
    }
}
