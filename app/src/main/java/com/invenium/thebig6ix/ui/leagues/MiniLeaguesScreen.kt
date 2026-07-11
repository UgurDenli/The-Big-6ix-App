package com.invenium.thebig6ix.ui.leagues

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.ui.home.ShimmerBox

private val Gold   = Color(0xFFFFD700)
private val Dim    = Color(0xFF888888)
private val CardBg = Color(0xFF111111)
private val Green  = Color(0xFF4CAF50)

// ── Head to Head screen ───────────────────────────────────────────────────────

@Composable
fun HeadToHeadScreen(
    viewModel: MiniLeagueViewModel,
    onBack: () -> Unit = {}
) {
    val entries      by viewModel.h2hEntries.collectAsState()
    val loading      by viewModel.h2hLoading.collectAsState()
    val opponentName by viewModel.h2hOpponentName.collectAsState()
    val gameweek     by viewModel.h2hGameweek.collectAsState()
    val maxGw        by viewModel.h2hMaxGw.collectAsState()
    val ironManFont  = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    val myTotalPts = entries.sumOf { it.myPoints }
    val opTotalPts = entries.sumOf { it.opponentPoints }

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
                    "HEAD TO HEAD",
                    color = Gold, fontFamily = ironManFont, fontSize = 18.sp, letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            // GW selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = { if (gameweek > 1) viewModel.setH2hGw(gameweek - 1) },
                    enabled  = gameweek > 1 && !loading
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Previous GW",
                        tint = if (gameweek > 1 && !loading) Gold else Color(0xFF333333)
                    )
                }
                Text(
                    "GAMEWEEK $gameweek",
                    color = Gold, fontFamily = ironManFont, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                IconButton(
                    onClick  = { if (gameweek < maxGw) viewModel.setH2hGw(gameweek + 1) },
                    enabled  = gameweek < maxGw && !loading
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next GW",
                        tint = if (gameweek < maxGw && !loading) Gold else Color(0xFF333333)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Score summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1400)),
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("YOU", color = Green, fontFamily = ironManFont, fontSize = 10.sp, letterSpacing = 1.5.sp)
                        Text(
                            "$myTotalPts",
                            color = when {
                                myTotalPts > opTotalPts -> Green
                                myTotalPts < opTotalPts -> Color(0xFFF44336)
                                else -> Gold
                            },
                            fontFamily = ironManFont, fontSize = 32.sp, fontWeight = FontWeight.Bold
                        )
                        Text("pts", color = Dim, fontSize = 10.sp)
                    }
                    Text("⚔️", fontSize = 24.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            opponentName.uppercase().take(12),
                            color = Color.White, fontFamily = ironManFont, fontSize = 10.sp,
                            letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$opTotalPts",
                            color = when {
                                opTotalPts > myTotalPts -> Color(0xFFF44336)
                                opTotalPts < myTotalPts -> Green
                                else -> Gold
                            },
                            fontFamily = ironManFont, fontSize = 32.sp, fontWeight = FontWeight.Bold
                        )
                        Text("pts", color = Dim, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(5) {
                            ShimmerBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚽", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No fixtures for GW$gameweek",
                                color = Dim, fontFamily = ironManFont, fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(entries) { entry ->
                            H2HMatchCard(entry, ironManFont)
                        }
                    }
                }
            }
        }
    }
}

// ── Match comparison card ─────────────────────────────────────────────────────

@Composable
private fun H2HMatchCard(entry: HeadToHeadEntry, ironManFont: FontFamily) {
    val isScored = entry.actualHome >= 0
    val myWon    = isScored && entry.myPoints > entry.opponentPoints
    val opWon    = isScored && entry.opponentPoints > entry.myPoints

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = CardBg),
        shape    = RoundedCornerShape(10.dp),
        border   = BorderStroke(
            1.dp,
            when {
                myWon -> Green.copy(alpha = 0.4f)
                opWon -> Color(0xFFF44336).copy(alpha = 0.3f)
                else  -> Color(0xFF2A2A2A)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${entry.homeTeam}  vs  ${entry.awayTeam}",
                color = Color.White, fontFamily = ironManFont, fontSize = 11.sp,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (isScored) {
                Spacer(Modifier.height(2.dp))
                Text("Result: ${entry.actualHome} – ${entry.actualAway}", color = Dim, fontSize = 10.sp)
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // My pick
                PickBox(
                    predText  = if (entry.myPredHome >= 0) "${entry.myPredHome} – ${entry.myPredAway}" else "—",
                    pts       = entry.myPoints,
                    label     = "YOU",
                    won       = myWon,
                    isScored  = isScored,
                    wonColor  = Green,
                    labelColor = Green,
                    modifier  = Modifier.weight(1f)
                )

                Text("⚔️", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))

                // Opponent pick
                PickBox(
                    predText  = if (entry.opponentPredHome >= 0) "${entry.opponentPredHome} – ${entry.opponentPredAway}" else "—",
                    pts       = entry.opponentPoints,
                    label     = "THEM",
                    won       = opWon,
                    isScored  = isScored,
                    wonColor  = Color(0xFFF44336),
                    labelColor = Dim,
                    modifier  = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PickBox(
    predText: String,
    pts: Int,
    label: String,
    won: Boolean,
    isScored: Boolean,
    wonColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (won) wonColor.copy(alpha = 0.12f) else Color(0xFF1A1A1A),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    0.5.dp,
                    if (won) wonColor.copy(alpha = 0.5f) else Color(0xFF2A2A2A),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    predText,
                    color = if (won) wonColor else Color.White,
                    fontFamily = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold)),
                    fontSize = 15.sp
                )
                if (isScored) {
                    Text(
                        "$pts pts",
                        color = if (won) wonColor else Gold,
                        fontFamily = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold)),
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = labelColor, fontSize = 8.sp, letterSpacing = 1.sp,
            fontFamily = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold)))
    }
}
