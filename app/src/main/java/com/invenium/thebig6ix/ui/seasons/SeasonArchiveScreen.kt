package com.invenium.thebig6ix.ui.seasons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.ui.home.ShimmerBox
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gold   = Color(0xFFFFD700)
private val Dim    = Color(0xFF888888)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)
private val CardBg = Color(0xFF111111)

data class SeasonEntry(
    val id: String,
    val label: String,
    val archivedAt: Date?,
    val topScorers: List<Pair<String, Int>>  // (fullName, score)
)

@Composable
fun SeasonArchiveScreen(
    onBack:            () -> Unit = {},
    onSeasonSelected:  (id: String, label: String) -> Unit = { _, _ -> },
) {
    val db = FirebaseFirestore.getInstance()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var seasons   by remember { mutableStateOf<List<SeasonEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("seasons").get().await()
            seasons = snap.documents.mapNotNull { doc ->
                val label       = doc.getString("season") ?: doc.getString("name") ?: doc.id
                val archivedAt  = doc.getTimestamp("archivedAt")?.toDate()

                // topPlayers is an array of maps: [{fullName: "...", score: 100}, ...]
                @Suppress("UNCHECKED_CAST")
                val raw = doc.get("topPlayers") as? List<*>
                val topScorers = raw
                    ?.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        val name  = map["fullName"] as? String ?: return@mapNotNull null
                        val score = (map["score"] as? Long)?.toInt()
                            ?: (map["score"] as? Double)?.toInt()
                            ?: 0
                        Pair(name, score)
                    }
                    ?.sortedByDescending { it.second }
                    ?: emptyList()

                SeasonEntry(id = doc.id, label = label, archivedAt = archivedAt, topScorers = topScorers)
            }.sortedByDescending { it.archivedAt?.time ?: 0L }

            if (seasons.isEmpty()) errorMsg = "No archived seasons found yet."
        } catch (e: Exception) {
            errorMsg = "Failed to load seasons: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "SEASON ARCHIVE",
                    color = Gold, fontFamily = ironManFont, fontSize = 20.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(3) {
                            ShimmerBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }

                errorMsg != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏆", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                errorMsg ?: "",
                                color = Dim,
                                fontFamily = ironManFont,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(seasons) { index, season ->
                            SeasonCard(
                                season          = season,
                                seasonIndex     = index,
                                ironManFont     = ironManFont,
                                onClick         = { onSeasonSelected(season.id, season.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonCard(season: SeasonEntry, @Suppress("UNUSED_PARAMETER") seasonIndex: Int, ironManFont: FontFamily, onClick: () -> Unit = {}) {
    val dateStr = season.archivedAt?.let {
        SimpleDateFormat("d MMM yyyy", Locale.UK).format(it)
    } ?: "Unknown date"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(1.dp, Gold.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

            // Season header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        season.label,
                        color = Gold, fontFamily = ironManFont, fontSize = 17.sp, letterSpacing = 1.sp
                    )
                    Text("Archived $dateStr", color = Dim, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 28.sp)
                    Icon(
                        Icons.Default.ChevronRight, contentDescription = "View leaderboard",
                        tint = Gold.copy(alpha = 0.5f), modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (season.topScorers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF1E1E1E))
                Spacer(Modifier.height(10.dp))

                // Top 3 (or however many exist)
                season.topScorers.take(3).forEachIndexed { idx, (name, score) ->
                    val (emoji, color) = when (idx) {
                        0    -> "🥇" to Gold
                        1    -> "🥈" to Silver
                        else -> "🥉" to Bronze
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                        Text(
                            name,
                            color = color, fontFamily = ironManFont,
                            fontSize = 13.sp, modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("$score pts", color = color, fontFamily = ironManFont, fontSize = 12.sp)
                        }
                    }
                }

                if (season.topScorers.size > 3) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "+${season.topScorers.size - 3} more players",
                        color = Dim, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 30.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("No scoreboard data stored for this season.", color = Dim, fontSize = 11.sp)
            }
        }
    }
}

