package com.invenium.thebig6ix.ui.seasons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.ui.home.ShimmerBox
import kotlinx.coroutines.tasks.await

private val LbGold   = Color(0xFFFFD700)
private val LbSilver = Color(0xFFC0C0C0)
private val LbBronze = Color(0xFFCD7F32)
private val LbDim    = Color(0xFF888888)
private val LbCardBg = Color(0xFF0D0D0D)

data class SeasonUserScore(
    val userId: String,
    val fullName: String,
    val score: Int,
    val correctScores: Int,
    val gamesPlayed: Int,
    val bestGwScore: Int,
)

@Composable
fun SeasonLeaderboardScreen(
    seasonId:       String,
    seasonLabel:    String,
    onBack:         () -> Unit = {},
    onUserSelected: (userId: String, userName: String) -> Unit = { _, _ -> },
) {
    val db           = FirebaseFirestore.getInstance()
    val currentUid   = FirebaseAuth.getInstance().currentUser?.uid
    val ironManFont  = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var entries   by remember { mutableStateOf<List<SeasonUserScore>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    var page      by remember { mutableStateOf(0) }
    val pageSize  = 20

    LaunchedEffect(seasonId) {
        try {
            val snap = db.collection("seasons")
                .document(seasonId)
                .collection("userScores")
                .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()

            entries = snap.documents.mapNotNull { doc ->
                val name = doc.getString("fullName") ?: return@mapNotNull null
                SeasonUserScore(
                    userId       = doc.id,
                    fullName     = name,
                    score        = (doc.getLong("score")         ?: 0L).toInt(),
                    correctScores= (doc.getLong("correctScores") ?: 0L).toInt(),
                    gamesPlayed  = (doc.getLong("gamesPlayed")   ?: 0L).toInt(),
                    bestGwScore  = (doc.getLong("bestGwScore")   ?: 0L).toInt(),
                )
            }
            if (entries.isEmpty()) errorMsg = "No data found for this season."
        } catch (e: Exception) {
            errorMsg = "Failed to load: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val totalPages   = maxOf(1, (entries.size + pageSize - 1) / pageSize)
    val pagedEntries = entries.drop(page * pageSize).take(pageSize)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(seasonLabel, color = LbGold, fontFamily = ironManFont, fontSize = 17.sp)
                    Text("FULL LEADERBOARD", color = LbDim, fontFamily = ironManFont, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(6) {
                            ShimmerBox(Modifier.fillMaxWidth().height(60.dp).padding(vertical = 2.dp))
                        }
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            errorMsg ?: "",
                            color = LbDim, fontFamily = ironManFont,
                            fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                else -> {
                    // Column headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rank", color = LbDim, fontFamily = ironManFont, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                        Text("Name", color = LbDim, fontFamily = ironManFont, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Score", color = LbDim, fontFamily = ironManFont, fontSize = 12.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        itemsIndexed(pagedEntries) { idx, entry ->
                            val globalRank = page * pageSize + idx + 1
                            SeasonLeaderboardRow(
                                rank          = globalRank,
                                entry         = entry,
                                isCurrentUser = entry.userId == currentUid,
                                ironManFont   = ironManFont,
                                onClick       = { onUserSelected(entry.userId, entry.fullName) }
                            )
                        }
                    }

                    // Pagination
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (page > 0) page-- },
                            enabled = page > 0,
                            colors  = ButtonDefaults.buttonColors(
                                containerColor         = LbGold,
                                disabledContainerColor = Color(0xFF333333)
                            )
                        ) { Text("Previous", fontFamily = ironManFont, color = Color.Black, fontSize = 13.sp) }

                        Text(
                            "Page ${page + 1} of $totalPages",
                            color = Color.White, fontFamily = ironManFont, fontSize = 12.sp
                        )

                        Button(
                            onClick = { if (page < totalPages - 1) page++ },
                            enabled = page < totalPages - 1,
                            colors  = ButtonDefaults.buttonColors(
                                containerColor         = LbGold,
                                disabledContainerColor = Color(0xFF333333)
                            )
                        ) { Text("Next", fontFamily = ironManFont, color = Color.Black, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonLeaderboardRow(
    rank: Int,
    entry: SeasonUserScore,
    isCurrentUser: Boolean,
    ironManFont: FontFamily,
    onClick: () -> Unit = {},
) {
    val borderColor = when {
        isCurrentUser -> LbGold
        rank == 1     -> LbGold
        rank == 2     -> LbSilver
        rank == 3     -> LbBronze
        else          -> Color(0xFF222222)
    }
    val bgColor = if (isCurrentUser) Color(0xFF1A1400) else LbCardBg

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor),
        border   = androidx.compose.foundation.BorderStroke(if (isCurrentUser) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                "$rank",
                fontFamily = ironManFont,
                fontSize   = 14.sp,
                color      = if (rank <= 3) LbGold else LbDim,
                modifier   = Modifier.width(32.dp)
            )

            // Trophy for #1
            if (rank == 1) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint     = LbGold,
                    modifier = Modifier.size(16.dp).padding(end = 2.dp)
                )
            }

            // Name + sub-stats
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        entry.fullName,
                        fontFamily = ironManFont, fontSize = 14.sp,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrentUser) {
                        Box(
                            modifier = Modifier
                                .background(LbGold, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("YOU", fontFamily = ironManFont, fontSize = 9.sp, color = Color.Black)
                        }
                    }
                }
                if (entry.gamesPlayed > 0) {
                    Text(
                        "${entry.correctScores} correct · ${entry.gamesPlayed} played · best GW: ${entry.bestGwScore}pts",
                        fontSize = 10.sp, color = Color(0xFF666666)
                    )
                }
            }

            // Score
            Text(
                "${entry.score}",
                fontFamily = ironManFont, fontSize = 16.sp,
                color = LbGold, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End
            )
        }
    }
}
