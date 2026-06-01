package com.invenium.thebig6ix.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

data class PredictionAuditEntry(
    val userName: String,
    val homeTeam: String,
    val awayTeam: String,
    val predictedHome: Int,
    val predictedAway: Int,
    val actualHome: Int,
    val actualAway: Int,
    val points: Int,
    val wildcardUsed: Boolean,
    val captainUsed: Boolean
)

class AdminViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ── Fixture Editor ────────────────────────────────────────────────────────

    private val _fixturesForGw = MutableStateFlow<List<FootballFixture>>(emptyList())
    val fixturesForGw: StateFlow<List<FootballFixture>> = _fixturesForGw

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ── General ───────────────────────────────────────────────────────────────

    /** True while a Cloud Function call is in-flight. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    /** One-shot toast messages. Cleared after consumption. */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun clearToast() { _toast.value = null }

    // ── Points Audit ──────────────────────────────────────────────────────────

    private val _auditEntries = MutableStateFlow<List<PredictionAuditEntry>>(emptyList())
    val auditEntries: StateFlow<List<PredictionAuditEntry>> = _auditEntries

    private val _isAuditLoading = MutableStateFlow(false)
    val isAuditLoading: StateFlow<Boolean> = _isAuditLoading

    // ── Load points audit for a gameweek ─────────────────────────────────────

    fun loadPointsAudit(gameweek: Int) {
        viewModelScope.launch {
            _isAuditLoading.value = true
            try {
                // Fetch all predictions for this gameweek
                val predSnap = db.collection("predictions")
                    .whereEqualTo("gameweek", gameweek)
                    .get().await()

                // Fetch fixtures for this gameweek
                val fixtureSnap = db.collection("fixtures")
                    .whereEqualTo("gameweek", gameweek)
                    .get().await()
                val fixtureMap = fixtureSnap.documents.associate { doc ->
                    doc.id to Pair(
                        doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                        doc.getLong("awayTeamGoals")?.toInt() ?: -1
                    )
                }

                // Fetch user names
                val userIds = predSnap.documents.mapNotNull { it.getString("userId") }.distinct()
                val userMap = mutableMapOf<String, String>()
                userIds.chunked(30).forEach { chunk ->
                    db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                        .documents.forEach { doc ->
                            userMap[doc.id] = doc.getString("fullName") ?: "Unknown"
                        }
                }

                val entries = predSnap.documents.mapNotNull { doc ->
                    val fixtureId = doc.getString("fixtureId") ?: return@mapNotNull null
                    val (actualHome, actualAway) = fixtureMap[fixtureId] ?: Pair(-1, -1)
                    if (actualHome < 0) return@mapNotNull null // only scored fixtures
                    val userId = doc.getString("userId") ?: return@mapNotNull null
                    PredictionAuditEntry(
                        userName = userMap[userId] ?: "Unknown",
                        homeTeam = doc.getString("homeTeam") ?: "",
                        awayTeam = doc.getString("awayTeam") ?: "",
                        predictedHome = doc.getLong("homeTeamGoals")?.toInt() ?: 0,
                        predictedAway = doc.getLong("awayTeamGoals")?.toInt() ?: 0,
                        actualHome = actualHome,
                        actualAway = actualAway,
                        points = doc.getLong("points")?.toInt() ?: 0,
                        wildcardUsed = doc.getBoolean("wildcardUsed") ?: false,
                        captainUsed = doc.getBoolean("captainUsed") ?: false
                    )
                }.sortedWith(compareByDescending<PredictionAuditEntry> { it.points }.thenBy { it.userName })

                _auditEntries.value = entries
            } catch (e: Exception) {
                _toast.value = "Audit failed: ${e.message}"
            } finally {
                _isAuditLoading.value = false
            }
        }
    }

    // ── Load fixtures for a gameweek ──────────────────────────────────────────

    fun loadFixturesByGameweek(gameweek: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snap = db.collection("fixtures")
                    .whereEqualTo("gameweek", gameweek)
                    .get().await()
                _fixturesForGw.value = snap.mapNotNull { doc ->
                    runCatching {
                        FootballFixture(
                            id            = doc.id,
                            homeTeam      = doc.getString("homeTeam")      ?: return@mapNotNull null,
                            awayTeam      = doc.getString("awayTeam")      ?: return@mapNotNull null,
                            date          = doc.getString("date")          ?: "",
                            homeTeamGoals = doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                            awayTeamGoals = doc.getLong("awayTeamGoals")?.toInt() ?: -1,
                            winner        = doc.getString("winner")        ?: "",
                            deadline      = doc.getTimestamp("deadline"),
                            gameweek      = doc.getLong("gameweek")?.toInt() ?: 0
                        )
                    }.getOrNull()
                }.sortedBy { it.homeTeam }
            } catch (e: Exception) {
                _toast.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Update fixture score ──────────────────────────────────────────────────

    fun updateFixtureScore(fixtureId: String, homeGoals: Int, awayGoals: Int) {
        viewModelScope.launch {
            try {
                db.collection("fixtures").document(fixtureId)
                    .update("homeTeamGoals", homeGoals, "awayTeamGoals", awayGoals)
                    .await()
                // Patch in-memory list so UI refreshes instantly
                _fixturesForGw.value = _fixturesForGw.value.map { f ->
                    if (f.id == fixtureId) f.copy(homeTeamGoals = homeGoals, awayTeamGoals = awayGoals) else f
                }
                _toast.value = "Score saved ✓"
            } catch (e: Exception) {
                _toast.value = "Save failed: ${e.message}"
            }
        }
    }

    // ── Create fixture ────────────────────────────────────────────────────────

    fun createFixture(homeTeam: String, awayTeam: String, gameweek: Int, deadlineMs: Long) {
        viewModelScope.launch {
            try {
                db.collection("fixtures").add(
                    mapOf(
                        "homeTeam"      to homeTeam.trim(),
                        "awayTeam"      to awayTeam.trim(),
                        "gameweek"      to gameweek,
                        "deadline"      to Timestamp(Date(deadlineMs)),
                        "homeTeamGoals" to -1,
                        "awayTeamGoals" to -1,
                        "winner"        to "",
                        "date"          to ""
                    )
                ).await()
                _toast.value = "Fixture created ✓"
            } catch (e: Exception) {
                _toast.value = "Create failed: ${e.message}"
            }
        }
    }

    // ── Trigger scoring via Cloud Function ────────────────────────────────────

    fun triggerScoring() {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val (code, body) = httpPost(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/manualCalculatePoints",
                    token = null
                )
                _toast.value = if (code == 200) "Scoring triggered ✓" else "HTTP $code: $body"
            } catch (e: Exception) {
                _toast.value = "Error: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Season reset via Cloud Function ──────────────────────────────────────

    fun triggerSeasonReset() {
        val user = auth.currentUser ?: run { _toast.value = "Not logged in"; return }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val idToken = user.getIdToken(false).await().token
                    ?: run { _toast.value = "Could not get ID token"; return@launch }
                val (code, body) = httpPost(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/resetSeason",
                    token = idToken
                )
                _toast.value = if (code == 200) "Season reset complete ✓" else "HTTP $code: $body"
            } catch (e: Exception) {
                _toast.value = "Error: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    private suspend fun httpPost(url: String, token: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60_000
                readTimeout    = 60_000
                doOutput       = false
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val code = conn.responseCode
            val body = runCatching { conn.inputStream.bufferedReader().readText() }
                .getOrElse { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            conn.disconnect()
            Pair(code, body)
        }
}
