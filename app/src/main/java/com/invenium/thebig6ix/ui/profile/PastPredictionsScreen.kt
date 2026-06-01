package com.invenium.thebig6ix.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.flagFor
import com.invenium.thebig6ix.ui.home.ShimmerBox
import kotlinx.coroutines.tasks.await

private val Gold         = Color(0xFFFFD700)
private val Dim          = Color(0xFF888888)
private val CardBg       = Color(0xFF111111)
private val ResultGreen  = Color(0xFF4CAF50)   // correct score
private val ResultYellow = Color(0xFFFFD700)   // correct result
private val ResultRed    = Color(0xFFF44336)   // wrong

private data class PastPrediction(
    val homeTeam: String,
    val awayTeam: String,
    val predictedHome: Int,
    val predictedAway: Int,
    val actualHome: Int,
    val actualAway: Int,
    val awardedPoints: Int,
    val isScored: Boolean,
    val fixtureExists: Boolean,   // false when the fixture doc was deleted
    val gameweek: Int,
    val wildcardUsed: Boolean,
    val captainUsed: Boolean
)

@Composable
fun PastPredictionsScreen(userId: String? = null, onBack: (() -> Unit)? = null) {
    val auth = FirebaseAuth.getInstance()
    val resolvedUserId = userId ?: auth.currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var allPredictions by remember { mutableStateOf<List<PastPrediction>>(emptyList()) }
    var availableGameweeks by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedGameweek by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(resolvedUserId) {
        isLoading = true
        val predDocs = db.collection("predictions")
            .whereEqualTo("userId", resolvedUserId)
            .get().await()

        if (predDocs.isEmpty) {
            isLoading = false
            return@LaunchedEffect
        }

        val fixtureIds = predDocs.documents.mapNotNull { it.getString("fixtureId") }.distinct()
        val fixturesMap = mutableMapOf<String, Pair<Int, Int>>()
        fixtureIds.chunked(30).forEach { chunk ->
            db.collection("fixtures")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
                .documents.forEach { doc ->
                    fixturesMap[doc.id] = Pair(
                        doc.getLong("homeTeamGoals")?.toInt() ?: -1,
                        doc.getLong("awayTeamGoals")?.toInt() ?: -1
                    )
                }
        }

        val parsed = predDocs.documents.mapNotNull { doc ->
            val fixtureId = doc.getString("fixtureId") ?: return@mapNotNull null
            val fixtureEntry = fixturesMap[fixtureId]
            val fixtureExists = fixtureEntry != null
            val (actualHome, actualAway) = fixtureEntry ?: Pair(-1, -1)
            // Also treat as scored if the prediction document itself was already awarded points
            val wasAwarded = (doc.getLong("awardedPoints")?.toInt() ?: 0) > 0 ||
                              doc.getBoolean("scoredPoints") == true
            PastPrediction(
                homeTeam = doc.getString("homeTeam") ?: "Home",
                awayTeam = doc.getString("awayTeam") ?: "Away",
                predictedHome = doc.getLong("homeTeamGoals")?.toInt() ?: 0,
                predictedAway = doc.getLong("awayTeamGoals")?.toInt() ?: 0,
                actualHome = actualHome,
                actualAway = actualAway,
                awardedPoints = doc.getLong("awardedPoints")?.toInt() ?: 0,
                isScored = actualHome >= 0 || wasAwarded,
                fixtureExists = fixtureExists,
                gameweek = doc.getLong("gameweek")?.toInt() ?: 0,
                wildcardUsed = doc.getBoolean("wildcardUsed") ?: false,
                captainUsed = doc.getBoolean("captainUsed") ?: false
            )
        }

        val gws = parsed.map { it.gameweek }.distinct().sortedDescending()
        allPredictions = parsed
        availableGameweeks = gws
        selectedGameweek = gws.firstOrNull()
        isLoading = false
    }

    val displayPredictions = allPredictions.filter { it.gameweek == selectedGameweek }
    val gwTotal = displayPredictions.filter { it.isScored }.sumOf { it.awardedPoints }
    // True when every prediction in the GW has a deleted/missing fixture
    val allOrphaned = displayPredictions.isNotEmpty() && displayPredictions.none { it.fixtureExists }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PAST PREDICTIONS",
                    color = Gold,
                    fontFamily = ironManFont,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
            }

            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.Start).padding(start = 4.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    repeat(5) {
                        ShimmerBox(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp)))
                    }
                }
                return@Surface
            }

            if (availableGameweeks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No predictions yet.", color = Dim, fontSize = 14.sp)
                }
                return@Surface
            }

            // Gameweek tabs
            val selectedIndex = availableGameweeks.indexOf(selectedGameweek).coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = Color(0xFF0D0D0D),
                contentColor = Gold,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedIndex < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedIndex])
                                .height(2.dp)
                                .background(Gold)
                        )
                    }
                },
                divider = {}
            ) {
                availableGameweeks.forEach { gw ->
                    val sel = selectedGameweek == gw
                    Tab(
                        selected = sel,
                        onClick = { selectedGameweek = gw },
                        text = {
                            Text(
                                "GW $gw",
                                color = if (sel) Gold else Dim,
                                fontFamily = ironManFont,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // GW summary bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gameweek $selectedGameweek",
                    color = Dim,
                    fontFamily = ironManFont,
                    fontSize = 13.sp
                )
                Text(
                    "$gwTotal pts",
                    color = Gold,
                    fontFamily = ironManFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)

            // Banner for gameweeks where the fixture data no longer exists
            if (allOrphaned) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1200))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚠️", fontSize = 14.sp)
                    Text(
                        "Match data no longer available for this gameweek — results couldn't be scored.",
                        color = Color(0xFFAA8800),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayPredictions) { pred ->
                    PredictionResultCard(pred, ironManFont)
                }
            }
        }
    }
}

@Composable
private fun PredictionResultCard(pred: PastPrediction, ironManFont: FontFamily) {
    val resultColor = when {
        !pred.isScored && !pred.fixtureExists -> Color(0xFF2A2A2A)  // orphaned — very dim
        !pred.isScored                        -> Color(0xFF333333)  // pending
        pred.awardedPoints >= 3               -> ResultGreen        // correct score
        pred.awardedPoints >= 1               -> ResultYellow       // correct result
        else                                  -> ResultRed          // wrong
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, resultColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val homeFlag = flagFor(pred.homeTeam)
                    if (homeFlag.isNotEmpty()) Text(homeFlag, fontSize = 14.sp)
                    Text(
                        pred.homeTeam,
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${pred.predictedHome} - ${pred.predictedAway}",
                        color = if (pred.isScored) resultColor else Color.White,
                        fontFamily = ironManFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    when {
                        pred.isScored && pred.actualHome >= 0 -> {
                            // Normal case: fixture exists and has a real result
                            Text(
                                "${pred.actualHome} - ${pred.actualAway}",
                                color = Dim,
                                fontSize = 11.sp
                            )
                        }
                        pred.isScored -> {
                            // Scored via awardedPoints but fixture no longer has a live score
                            Text("Scored", color = Dim, fontSize = 11.sp)
                        }
                        !pred.fixtureExists -> {
                            // Fixture document was deleted — can't determine result
                            Text(
                                "Result unavailable",
                                color = Dim.copy(alpha = 0.45f),
                                fontSize = 11.sp
                            )
                        }
                        else -> {
                            // Fixture exists but match hasn't happened yet
                            Text("Pending", color = Dim, fontSize = 11.sp)
                        }
                    }
                }

                // Away team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    // Right-align: flag on right of name
                ) {
                    Text(
                        pred.awayTeam,
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    val awayFlag = flagFor(pred.awayTeam)
                    if (awayFlag.isNotEmpty()) Text(awayFlag, fontSize = 14.sp)
                }
            }

            if (pred.isScored || pred.wildcardUsed || pred.captainUsed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pred.captainUsed) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0D1A2E), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("🎖️ Captain", color = Color(0xFF4B9EFF), fontFamily = ironManFont, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (pred.wildcardUsed) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2A1A40), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("🃏 Wildcard", color = Color(0xFF9C6ADE), fontFamily = ironManFont, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (pred.isScored) {
                        val label = when {
                            pred.awardedPoints == 0 -> "✗ Wrong"
                            pred.awardedPoints >= 3 -> "★ Correct Score"
                            else -> "~ Correct Result"
                        }
                        Box(
                            modifier = Modifier
                                .background(resultColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(label, color = resultColor, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
