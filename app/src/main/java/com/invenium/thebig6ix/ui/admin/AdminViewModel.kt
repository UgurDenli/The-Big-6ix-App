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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

data class CsvFixture(
    val homeTeam: String,
    val awayTeam: String
)

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

    // ── Load points audit for a gameweek (via Cloud Function) ────────────────

    fun loadPointsAudit(gameweek: Int) {
        val user = auth.currentUser ?: run { _toast.value = "Not logged in"; return }
        viewModelScope.launch {
            _isAuditLoading.value = true
            try {
                val idToken = user.getIdToken(false).await().token
                    ?: run { _toast.value = "Could not get ID token"; return@launch }
                val (code, body) = httpGet(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/adminGetPointsAudit?gameweek=$gameweek",
                    token = idToken
                )
                if (code != 200) {
                    _toast.value = "Audit failed (HTTP $code)"
                    return@launch
                }
                // Parse JSON array manually (avoid adding a JSON library)
                val entries = parseAuditJson(body)
                _auditEntries.value = entries
            } catch (e: Exception) {
                _toast.value = "Audit failed: ${e.message}"
            } finally {
                _isAuditLoading.value = false
            }
        }
    }

    private fun parseAuditJson(json: String): List<PredictionAuditEntry> {
        // Minimal JSON parser for the audit entries array
        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o: JSONObject = arr.getJSONObject(i)
                PredictionAuditEntry(
                    userName      = o.optString("userName", "Unknown"),
                    homeTeam      = o.optString("homeTeam", ""),
                    awayTeam      = o.optString("awayTeam", ""),
                    predictedHome = o.optInt("predictedHome", 0),
                    predictedAway = o.optInt("predictedAway", 0),
                    actualHome    = o.optInt("actualHome", -1),
                    actualAway    = o.optInt("actualAway", -1),
                    points        = o.optInt("points", 0),
                    wildcardUsed  = o.optBoolean("wildcardUsed", false),
                    captainUsed   = o.optBoolean("captainUsed", false)
                )
            }
        } catch (_: Exception) { emptyList() }
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

    // ── Reset all user tokens via Cloud Function ─────────────────────────────

    fun resetAllTokens(onDone: (String) -> Unit = {}) {
        val user = auth.currentUser ?: run { _toast.value = "Not logged in"; return }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val idToken = user.getIdToken(false).await().token
                    ?: run { _toast.value = "Could not get ID token"; return@launch }
                val (code, body) = httpPost(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/adminResetAllTokens",
                    token = idToken
                )
                val msg = if (code == 200) "Tokens reset ✓" else "Failed (HTTP $code): $body"
                _toast.value = msg
                if (code == 200) onDone(msg)
            } catch (e: Exception) {
                _toast.value = "Token reset failed: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Reset tokens for a single user via Cloud Function ────────────────────

    fun resetUserTokensByName(name: String, onDone: (String) -> Unit = {}) {
        val user = auth.currentUser ?: run { _toast.value = "Not logged in"; return }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val idToken = user.getIdToken(false).await().token
                    ?: run { _toast.value = "Could not get ID token"; return@launch }
                val (code, body) = httpPostJson(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/adminResetUserTokens",
                    token = idToken,
                    json  = """{"name":"${name.trim().replace("\"","\\\"")}" }"""
                )
                val msg = if (code == 200) "Tokens reset for ${name.trim()} ✓" else "Failed (HTTP $code): $body"
                _toast.value = msg
                if (code == 200) onDone(msg)
            } catch (e: Exception) {
                _toast.value = "Reset failed: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Send GW winner push notification via Cloud Function ───────────────────

    fun sendGwWinnerNotification(gameweek: Int) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val (code, body) = httpPost(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/sendGwWinnerNotification?gameweek=$gameweek",
                    token = null
                )
                _toast.value = if (code == 200) "GW$gameweek notification sent ✓" else "HTTP $code: $body"
            } catch (e: Exception) {
                _toast.value = "Error: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Send custom push notification to all users ────────────────────────────

    fun sendCustomNotification(title: String, body: String) {
        val user = auth.currentUser ?: run { _toast.value = "Not logged in"; return }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val idToken = user.getIdToken(false).await().token
                    ?: run { _toast.value = "Could not get ID token"; return@launch }
                val (code, resp) = httpPostJson(
                    url   = "https://us-central1-the-big-6ix.cloudfunctions.net/sendCustomNotification",
                    token = idToken,
                    json  = """{"title":"${title.replace("\"","\\\"")}", "body":"${body.replace("\"","\\\"")}" }"""
                )
                _toast.value = if (code == 200) "Notification sent ✓" else "HTTP $code: $resp"
            } catch (e: Exception) {
                _toast.value = "Error: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Parse CSV text into fixture list ──────────────────────────────────────

    fun parseCsvFixtures(text: String): List<CsvFixture> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                // Supports: "HomeTeam vs AwayTeam" or "HomeTeam - AwayTeam" or "HomeTeam,AwayTeam"
                val parts = when {
                    line.contains(" vs ", ignoreCase = true) ->
                        line.split(Regex(" vs ", RegexOption.IGNORE_CASE), 2)
                    line.contains(" - ") ->
                        line.split(" - ", limit = 2)
                    line.contains(",") ->
                        line.split(",", limit = 2)
                    else -> return@mapNotNull null
                }
                if (parts.size == 2) CsvFixture(parts[0].trim(), parts[1].trim()) else null
            }

    // ── Bulk-create fixtures from CSV ────────────────────────────────────────

    fun importFixtures(fixtures: List<CsvFixture>, gameweek: Int, deadlineMs: Long) {
        if (fixtures.isEmpty()) { _toast.value = "No fixtures to import"; return }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                fixtures.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { f ->
                        val ref = db.collection("fixtures").document()
                        batch.set(ref, mapOf(
                            "homeTeam"      to f.homeTeam,
                            "awayTeam"      to f.awayTeam,
                            "gameweek"      to gameweek,
                            "deadline"      to Timestamp(Date(deadlineMs)),
                            "homeTeamGoals" to -1,
                            "awayTeamGoals" to -1,
                            "winner"        to "",
                            "date"          to ""
                        ))
                    }
                    batch.commit().await()
                }
                _toast.value = "Imported ${fixtures.size} fixture${if (fixtures.size != 1) "s" else ""} ✓"
            } catch (e: Exception) {
                _toast.value = "Import failed: ${e.message}"
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

    // ── HTTP helpers ──────────────────────────────────────────────────────────

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

    private suspend fun httpGet(url: String, token: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 60_000
                readTimeout    = 60_000
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val code = conn.responseCode
            val body = runCatching { conn.inputStream.bufferedReader().readText() }
                .getOrElse { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            conn.disconnect()
            Pair(code, body)
        }

    private suspend fun httpPostJson(url: String, token: String?, json: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60_000
                readTimeout    = 60_000
                doOutput       = true
                setRequestProperty("Content-Type", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            conn.outputStream.bufferedWriter().use { it.write(json) }
            val code = conn.responseCode
            val body = runCatching { conn.inputStream.bufferedReader().readText() }
                .getOrElse { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            conn.disconnect()
            Pair(code, body)
        }
}
