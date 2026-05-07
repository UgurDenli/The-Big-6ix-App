package com.invenium.thebig6ix.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.invenium.thebig6ix.ui.home.LeaderboardUser
import androidx.compose.ui.text.style.TextAlign

private val Gold    = Color(0xFFFFD700)
private val Silver  = Color(0xFFC0C0C0)
private val Bronze  = Color(0xFFCD7F32)
private val CardBg  = Color(0xFF111111)
private val Dim     = Color(0xFF888888)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onViewFullLeaderboard: () -> Unit = {}
) {
    val leaderboard by viewModel.leaderboard.collectAsState()
    val latestGwWinnerUid by viewModel.latestGwWinnerUid.collectAsState()
    val latestGwNumber by viewModel.latestGwNumber.collectAsState()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D0D0D), Color.Black)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tbsix),
                        contentDescription = null,
                        modifier = Modifier.width(240.dp).height(64.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "LEADERBOARD",
                        color = Gold,
                        fontFamily = ironManFont,
                        fontSize = 22.sp,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(2.dp)
                            .background(Gold)
                    )
                }
            }

            // Rank list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(leaderboard.take(5)) { index, user ->
                    val isGwWinner = user.uid == latestGwWinnerUid
                    when (index) {
                        0 -> TopPlayerCard(user = user, isGwWinner = isGwWinner, gwNumber = latestGwNumber, ironManFont = ironManFont)
                        else -> LeaderboardRow(index = index, user = user, isGwWinner = isGwWinner, gwNumber = latestGwNumber, ironManFont = ironManFont)
                    }
                }
            }

            // CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onViewFullLeaderboard,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "View Full Leaderboard",
                        color = Color.Black,
                        fontFamily = ironManFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GwWinnerBadge(gwNumber: Int?, ironManFont: FontFamily) {
    val label = if (gwNumber != null) "⚡ GW$gwNumber" else "⚡ GW"
    Box(
        modifier = Modifier
            .background(Color(0xFF1A1200), RoundedCornerShape(4.dp))
            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = Gold, fontFamily = ironManFont, fontSize = 10.sp)
    }
}

@Composable
private fun TopPlayerCard(user: LeaderboardUser, isGwWinner: Boolean, gwNumber: Int?, ironManFont: FontFamily) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1400)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, Gold)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trophy badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Gold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🏆", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(2.dp, Gold, CircleShape)
            ) {
                if (user.profileImageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(user.profileImageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_account),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        color = Gold,
                        fontFamily = ironManFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (isGwWinner) {
                        Spacer(modifier = Modifier.width(6.dp))
                        GwWinnerBadge(gwNumber, ironManFont)
                    }
                }
                if (user.isNewLeader) {
                    Text("NEW LEADER", color = Gold.copy(alpha = 0.7f), fontFamily = ironManFont, fontSize = 11.sp)
                } else {
                    Text("1st Place", color = Dim, fontSize = 12.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${user.totalScore}",
                    color = Gold,
                    fontFamily = ironManFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
                Text("pts", color = Dim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    index: Int,
    user: LeaderboardUser,
    isGwWinner: Boolean,
    gwNumber: Int?,
    ironManFont: FontFamily
) {
    val rankColor = when (index) {
        1 -> Silver
        2 -> Bronze
        else -> Dim
    }
    val rankLabel = when (index) {
        1 -> "2nd"
        2 -> "3rd"
        3 -> "4th"
        4 -> "5th"
        else -> "#${index + 1}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, rankColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rankLabel,
                color = rankColor,
                fontFamily = ironManFont,
                fontSize = 14.sp,
                modifier = Modifier.width(36.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, rankColor, CircleShape)
            ) {
                if (user.profileImageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(user.profileImageUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_account),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.name, color = Color.White, fontFamily = ironManFont, fontSize = 15.sp)
                if (isGwWinner) {
                    Spacer(modifier = Modifier.height(2.dp))
                    GwWinnerBadge(gwNumber, ironManFont)
                }
            }

            Text(
                text = "${user.totalScore} pts",
                color = Gold,
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}
