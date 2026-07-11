package com.invenium.thebig6ix.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.invenium.thebig6ix.R
import kotlinx.coroutines.delay
import java.util.Date

private val Gold    = Color(0xFFFFD700)
private val Silver  = Color(0xFFC0C0C0)
private val Bronze  = Color(0xFFCD7F32)
private val CardBg  = Color(0xFF111111)
private val Dim     = Color(0xFF888888)
private val GreenOk = Color(0xFF4CAF50)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onViewFullLeaderboard: () -> Unit = {}
) {
    val state       by viewModel.uiState.collectAsState()
    val gwWinner    by viewModel.gwWinner.collectAsState()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (state.isLoading) {
            HomeSkeletonScreen()
        } else {
            HomeContent(state, gwWinner, ironManFont, onViewFullLeaderboard)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    gwWinner: GwWinner?,
    ironManFont: FontFamily,
    onViewFullLeaderboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Logo + title
        Image(
            painter = painterResource(id = R.drawable.ic_tbsix),
            contentDescription = null,
            modifier = Modifier.width(240.dp).height(64.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text("HOME", color = Gold, fontFamily = ironManFont, fontSize = 22.sp, letterSpacing = 3.sp)
        Box(modifier = Modifier.width(40.dp).height(2.dp).background(Gold))

        Spacer(Modifier.height(20.dp))

        // ── User stats card ────────────────────────────────────────────────
        UserStatsCard(state, ironManFont)
        Spacer(Modifier.height(14.dp))

        // ── Next GW card ───────────────────────────────────────────────────
        if (state.nextGwNumber != null) {
            NextGwCard(state, ironManFont)
            Spacer(Modifier.height(14.dp))
        }

        // ── Top 3 leaderboard preview ──────────────────────────────────────
        if (state.topUsers.isNotEmpty()) {
            SectionHeader("TOP PLAYERS", ironManFont)
            Spacer(Modifier.height(8.dp))
            state.topUsers.forEachIndexed { index, user ->
                LeaderboardPreviewRow(index + 1, user, ironManFont)
                if (index < state.topUsers.lastIndex) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── View full leaderboard CTA ──────────────────────────────────────
        Button(
            onClick = onViewFullLeaderboard,
            colors  = ButtonDefaults.buttonColors(containerColor = Gold),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                "VIEW FULL LEADERBOARD",
                color = Color.Black,
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── User stats card ───────────────────────────────────────────────────────────

@Composable
private fun UserStatsCard(state: HomeUiState, ironManFont: FontFamily) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1400)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape)
                    .border(2.dp, Gold, CircleShape).background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                if (state.userProfileImageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(state.userProfileImageUrl),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(painterResource(id = R.drawable.ic_account), null, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.userName.ifBlank { "Player" }, color = Color.White, fontFamily = ironManFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (state.userRank > 0) {
                    Text("Rank #${state.userRank}", color = Dim, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${state.userTotalPoints}", color = Gold, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text("pts", color = Dim, fontSize = 11.sp)
            }
        }
    }
}

// ── Next GW card ──────────────────────────────────────────────────────────────

@Composable
private fun NextGwCard(state: HomeUiState, ironManFont: FontFamily) {
    val allPredicted = state.nextGwPredicted >= state.nextGwFixtureCount && state.nextGwFixtureCount > 0
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1000L) }
    }
    val countdownText = remember(now, state.nextDeadlineMs) {
        val deadline = state.nextDeadlineMs ?: return@remember ""
        val diffMs   = deadline - now
        if (diffMs <= 0) return@remember "Closed"
        val totalHours = diffMs / 3_600_000L
        val days = totalHours / 24; val hours = totalHours % 24; val mins = (diffMs % 3_600_000L) / 60_000L
        when { days > 0 -> "${days}d ${hours}h"; hours > 0 -> "${hours}h ${mins}m"; else -> "${mins}m" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (allPredicted) Color(0xFF0A1F0A) else Color(0xFF111111)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (allPredicted) GreenOk.copy(alpha = 0.5f) else Color(0xFF333333))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("⚽", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("GAMEWEEK ${state.nextGwNumber}", color = Gold, fontFamily = ironManFont, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (allPredicted) {
                    Box(
                        modifier = Modifier.background(GreenOk.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(0.5.dp, GreenOk.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text("ALL DONE ✓", color = GreenOk, fontFamily = ironManFont, fontSize = 10.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatChip("PREDICTED", "${state.nextGwPredicted}/${state.nextGwFixtureCount}", if (allPredicted) GreenOk else Gold, ironManFont, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatChip("CLOSES IN", countdownText, Dim, ironManFont, Modifier.weight(1f))
            }
        }
    }
}

// ── Reused composables ────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, value: String, color: Color, ironManFont: FontFamily, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(label, color = Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontFamily = ironManFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeader(text: String, ironManFont: FontFamily) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF222222))
        Text("  $text  ", color = Dim, fontFamily = ironManFont, fontSize = 11.sp, letterSpacing = 2.sp)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF222222))
    }
}

// ── GW Winner Banner ─────────────────────────────────────────────────────────

@Composable
private fun GwWinnerBanner(winner: GwWinner, ironManFont: FontFamily) {
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Gold.copy(alpha = glowAlpha))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "GW${winner.gameweek} WINNER",
                    color = Gold, fontFamily = ironManFont, fontSize = 10.sp, letterSpacing = 1.5.sp
                )
                Text(
                    winner.name,
                    color = Color.White, fontFamily = ironManFont, fontSize = 16.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${winner.gwPoints}",
                    color = Gold, fontFamily = ironManFont, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
                Text("pts", color = Dim, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LeaderboardPreviewRow(rank: Int, user: LeaderboardUser, ironManFont: FontFamily) {
    val medalColor = when (rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> Dim }
    val emoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, medalColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .border(1.5.dp, medalColor, CircleShape).background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl != null) {
                    Image(rememberAsyncImagePainter(user.profileImageUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Image(painterResource(id = R.drawable.ic_account), null, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, color = Color.White, fontFamily = ironManFont, fontSize = 14.sp)
                if (user.isNewLeader) {
                    Text("NEW LEADER", color = Gold.copy(alpha = 0.7f), fontFamily = ironManFont, fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            Text("${user.totalScore} pts", color = Gold, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ── Skeleton loading ──────────────────────────────────────────────────────────

@Composable
fun HomeSkeletonScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        ShimmerBox(Modifier.width(240.dp).height(64.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.width(100.dp).height(18.dp).clip(RoundedCornerShape(4.dp)))
        Spacer(Modifier.height(24.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(14.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(14.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(14.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(14.dp))
        repeat(3) {
            ShimmerBox(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_translate"
    )
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF1A1A1A), Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
                start = Offset(translateAnim - 500f, 0f),
                end   = Offset(translateAnim, 0f)
            )
        )
    )
}
