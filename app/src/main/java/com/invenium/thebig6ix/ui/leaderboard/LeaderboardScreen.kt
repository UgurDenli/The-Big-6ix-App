package com.invenium.thebig6ix.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val Gold = Color(0xFFFFD700)

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel = viewModel(),
    onBack: () -> Unit = {},
    onViewUserPredictions: (String) -> Unit = {}
) {
    val communityUsers by viewModel.communityUsers.collectAsState()
    val panelScores by viewModel.panelScores.collectAsState()
    val communityPage by viewModel.communityPage.collectAsState()
    val panelPage by viewModel.panelPage.collectAsState()
    val latestGwWinnerUid by viewModel.latestGwWinnerUid.collectAsState()
    val latestGwNumber by viewModel.latestGwNumber.collectAsState()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Community Scores", "Panel Scores")

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Leaderboard",
                    color = Color(0xFFFFD700),
                    fontFamily = ironManFont,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Black,
                contentColor = Color(0xFFFFD700)
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 0) viewModel.setCommunityPage(0)
                            else viewModel.setPanelPage(0)
                        },
                        text = {
                            Text(
                                label,
                                color = if (selectedTab == index) Color(0xFFFFD700) else Color.White,
                                fontFamily = ironManFont,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                val pageCount = viewModel.communityPageCount()
                val start = communityPage * viewModel.pageSize
                val pageItems = communityUsers.drop(start).take(viewModel.pageSize)
                val globalOffset = start

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(pageItems) { localIndex, user ->
                        val index = globalOffset + localIndex
                        LeaderboardRow(
                            rank = index + 1,
                            name = user.name,
                            score = user.score,
                            profileImageUrl = user.profileImageUrl,
                            uid = user.uid,
                            isGwWinner = user.uid == latestGwWinnerUid,
                            gwNumber = latestGwNumber,
                            ironManFont = ironManFont,
                            onClick = { onViewUserPredictions(user.uid) }
                        )
                    }
                }

                PaginationControls(
                    currentPage = communityPage,
                    pageCount = pageCount,
                    ironManFont = ironManFont,
                    onPrev = { viewModel.setCommunityPage(communityPage - 1) },
                    onNext = { viewModel.setCommunityPage(communityPage + 1) }
                )
            } else {
                val pageCount = viewModel.panelPageCount()
                val start = panelPage * viewModel.pageSize
                val pageItems = panelScores.drop(start).take(viewModel.pageSize)
                val globalOffset = start

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(pageItems) { localIndex, panel ->
                        val index = globalOffset + localIndex
                        LeaderboardRow(
                            rank = index + 1,
                            name = panel.name,
                            score = panel.score,
                            profileImageUrl = null,
                            uid = null,
                            isGwWinner = false,
                            gwNumber = null,
                            ironManFont = ironManFont,
                            onClick = {}
                        )
                    }
                }

                PaginationControls(
                    currentPage = panelPage,
                    pageCount = pageCount,
                    ironManFont = ironManFont,
                    onPrev = { viewModel.setPanelPage(panelPage - 1) },
                    onNext = { viewModel.setPanelPage(panelPage + 1) }
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    name: String,
    score: Int,
    profileImageUrl: String?,
    uid: String?,
    isGwWinner: Boolean,
    gwNumber: Int?,
    ironManFont: FontFamily,
    onClick: () -> Unit
) {
    val borderColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.DarkGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(BorderStroke(2.dp, borderColor))
            .clickable(enabled = uid != null, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rank == 1) "🏆" else "#$rank",
                color = borderColor,
                fontFamily = ironManFont,
                fontSize = 18.sp,
                modifier = Modifier.width(44.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, borderColor, CircleShape)
            ) {
                if (profileImageUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(profileImageUrl),
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
                Text(text = name, color = Color.White, fontFamily = ironManFont, fontSize = 16.sp)
                if (isGwWinner && gwNumber != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1A1200), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("⚡ GW$gwNumber", color = Gold, fontFamily = ironManFont, fontSize = 10.sp)
                    }
                }
            }

            Text(
                text = "$score pts",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun PaginationControls(
    currentPage: Int,
    pageCount: Int,
    ironManFont: FontFamily,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = currentPage > 0) {
            Text("← Prev", color = if (currentPage > 0) Color(0xFFFFD700) else Color.DarkGray, fontFamily = ironManFont)
        }
        Text(
            text = "Page ${currentPage + 1} of $pageCount",
            color = Color.White,
            fontFamily = ironManFont,
            fontSize = 14.sp
        )
        TextButton(onClick = onNext, enabled = currentPage < pageCount - 1) {
            Text("Next →", color = if (currentPage < pageCount - 1) Color(0xFFFFD700) else Color.DarkGray, fontFamily = ironManFont)
        }
    }
}
