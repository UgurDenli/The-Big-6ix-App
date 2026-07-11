package com.invenium.thebig6ix.ui.seasons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.tasks.await

private data class ArchPred(
    val homeTeam:      String,
    val awayTeam:      String,
    val predictedHome: Int,
    val predictedAway: Int,
    val actualHome:    Int,
    val actualAway:    Int,
    val awardedPoints: Int,
    val gameweek:      Int,
)

@Composable
fun SeasonPredictionsScreen(
    seasonId:   String,
    seasonName: String,
    userId:     String,
    userName:   String,
    onBack:     () -> Unit = {},
) {
    val db          = FirebaseFirestore.getInstance()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    val Gold        = Color(0xFFFFD700)
    val Dim         = Color(0xFF888888)

    var predictions by remember { mutableStateOf<List<ArchPred>>(emptyList()) }
    var totalScore  by remember { mutableStateOf(0) }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seasonId, userId) {
        try {
            val doc = db.collection("seasons").document(seasonId)
                .collection("userPredictions").document(userId)
                .get().await()

            totalScore = (doc.getLong("totalScore") ?: 0L).toInt()

            @Suppress("UNCHECKED_CAST")
            val raw = doc.get("predictions") as? List<Map<String, Any>> ?: emptyList()
            predictions = raw.mapNotNull { p ->
                val home = p["homeTeam"] as? String ?: return@mapNotNull null
                val away = p["awayTeam"] as? String ?: return@mapNotNull null
                ArchPred(
                    homeTeam      = home,
                    awayTeam      = away,
                    predictedHome = (p["predictedHome"] as? Long)?.toInt() ?: -1,
                    predictedAway = (p["predictedAway"] as? Long)?.toInt() ?: -1,
                    actualHome    = (p["actualHome"]    as? Long)?.toInt() ?: -1,
                    actualAway    = (p["actualAway"]    as? Long)?.toInt() ?: -1,
                    awardedPoints = (p["awardedPoints"] as? Long)?.toInt() ?: 0,
                    gameweek      = (p["gameweek"]      as? Long)?.toInt() ?: 0,
                )
            }
            if (predictions.isEmpty()) errorMsg = "No archived predictions found."
        } catch (e: Exception) {
            errorMsg = "Failed to load: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val grouped = predictions
        .groupBy { it.gameweek }
        .entries
        .sortedBy { it.key }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(userName, color = Color.White, fontFamily = ironManFont, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SEASON PREDICTIONS", color = Dim, fontFamily = ironManFont, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMsg ?: "", color = Dim, fontFamily = ironManFont, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Season score banner
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1A1400), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(seasonName, color = Dim, fontFamily = ironManFont, fontSize = 12.sp)
                                    Text("$totalScore pts total", color = Gold, fontFamily = ironManFont, fontSize = 22.sp)
                                }
                                Text("🏆", fontSize = 32.sp)
                            }
                        }

                        // Grouped by gameweek
                        grouped.forEach { (gw, preds) ->
                            val sortedPreds = preds.sortedBy { it.homeTeam }
                            val gwPts = sortedPreds.sumOf { it.awardedPoints }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("GAMEWEEK $gw", color = Gold, fontFamily = ironManFont, fontSize = 13.sp, letterSpacing = 1.sp)
                                    Text("$gwPts pts", color = Dim, fontFamily = ironManFont, fontSize = 12.sp)
                                }
                            }

                            items(sortedPreds) { pred ->
                                PredictionArchiveRow(pred = pred, ironManFont = ironManFont)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionArchiveRow(pred: ArchPred, ironManFont: FontFamily) {
    val resultColor = when (pred.awardedPoints) {
        3    -> Color(0xFFFFD700)
        1    -> Color(0xFF4CAF50)
        else -> Color(0xFF555555)
    }
    val resultLabel = when (pred.awardedPoints) {
        3    -> "✓ 3"
        1    -> "○ 1"
        else -> "✗ 0"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home team
        Text(
            pred.homeTeam,
            fontFamily = ironManFont, fontSize = 11.sp, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(end = 6.dp)
        )

        // Score block
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBox(pred.predictedHome, resultColor, ironManFont)
                Text("—", fontSize = 10.sp, color = Color(0xFF555555))
                ScoreBox(pred.predictedAway, resultColor, ironManFont)
            }
            if (pred.actualHome >= 0) {
                Text(
                    "${pred.actualHome} - ${pred.actualAway}",
                    fontSize = 9.sp, color = Color(0xFF666666)
                )
            }
        }

        // Away team
        Text(
            pred.awayTeam,
            fontFamily = ironManFont, fontSize = 11.sp, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )

        // Points badge
        Text(
            resultLabel,
            fontFamily = ironManFont, fontSize = 11.sp, color = resultColor,
            modifier = Modifier.width(34.dp), textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ScoreBox(n: Int, color: Color, ironManFont: FontFamily) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (n >= 0) "$n" else "?",
            fontFamily = ironManFont, fontSize = 13.sp,
            color = if (n >= 0) color else Color(0xFF555555)
        )
    }
}
