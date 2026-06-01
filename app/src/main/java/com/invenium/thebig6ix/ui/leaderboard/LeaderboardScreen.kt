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
import com.invenium.thebig6ix.ui.home.ShimmerBox

private val Gold   = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)
private val Dim    = Color(0xFF888888)
private val CardBg = Color(0xFF111111)

@Composable
fun LeaderboardScreen(
    viewModel: LeaderboardViewModel = viewModel(),
    onBack: () -> Unit = {},
    onViewUserPredictions: (String) -> Unit = {}
) {
    val communityUsers  by viewModel.communityUsers.collectAsState()
    val panelScores     by viewModel.panelScores.collectAsState()
    val communityPage   by viewModel.communityPage.collectAsState()
    val panelPage       by viewModel.panelPage.collectAsState()
    val latestGwWinnerUid by viewModel.latestGwWinnerUid.collectAsState()
    val latestGwNumber  by viewModel.latestGwNumber.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val ironManFont     = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Community", "Panel")

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("LEADERBOARD", color = Gold, fontFamily = ironManFont, fontSize = 22.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D0D0D),
                contentColor = Gold,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(2.dp)
                                .background(Gold)
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = {
                            selectedTab = index
                            if (index == 0) viewModel.setCommunityPage(0)
                            else viewModel.setPanelPage(0)
                        },
                        text = {
                            Text(
                                label,
                                color = if (selectedTab == index) Gold else Dim,
                                fontFamily = ironManFont,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                LeaderboardSkeletonList()
            } else if (selectedTab == 0) {
                CommunityTab(
                    users          = communityUsers,
                    page           = communityPage,
                    pageSize       = viewModel.pageSize,
                    pageCount      = viewModel.communityPageCount(),
                    latestGwWinnerUid = latestGwWinnerUid,
                    latestGwNumber = latestGwNumber,
                    currentUserUid = viewModel.currentUserUid,
                    currentUserRank = viewModel.currentUserRank(),
                    currentUserScore = viewModel.currentUserScore(),
                    ironManFont    = ironManFont,
                    onPrev         = { viewModel.setCommunityPage(communityPage - 1) },
                    onNext         = { viewModel.setCommunityPage(communityPage + 1) },
                    onRowClick     = onViewUserPredictions
                )
            } else {
                PanelTab(
                    panels    = panelScores,
                    page      = panelPage,
                    pageSize  = viewModel.pageSize,
                    pageCount = viewModel.panelPageCount(),
                    ironManFont = ironManFont,
                    onPrev    = { viewModel.setPanelPage(panelPage - 1) },
                    onNext    = { viewModel.setPanelPage(panelPage + 1) }
                )
            }
        }
    }
}

@Composable
private fun CommunityTab(
    users: List<UserScore>,
    page: Int,
    pageSize: Int,
    pageCount: Int,
    latestGwWinnerUid: String?,
    latestGwNumber: Int?,
    currentUserUid: String?,
    currentUserRank: Int,
    currentUserScore: UserScore?,
    ironManFont: FontFamily,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onRowClick: (String) -> Unit
) {
    val start      = page * pageSize
    val pageItems  = users.drop(start).take(pageSize)
    val currentInPage = pageItems.any { it.uid == currentUserUid }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(pageItems) { localIdx, user ->
                val globalRank = start + localIdx + 1
                LeaderboardRow(
                    rank           = globalRank,
                    name           = user.name,
                    score          = user.score,
                    profileImageUrl = user.profileImageUrl,
                    uid            = user.uid,
                    isGwWinner     = user.uid == latestGwWinnerUid,
                    gwNumber       = latestGwNumber,
                    isCurrentUser  = user.uid == currentUserUid,
                    ironManFont    = ironManFont,
                    onClick        = { onRowClick(user.uid) }
                )
            }
        }

        // Pinned "YOU" row — only shown when current user is not visible on this page
        if (!currentInPage && currentUserScore != null && currentUserRank > 0) {
            HorizontalDivider(color = Color(0xFF2A2A2A))
            PinnedYouRow(
                rank       = currentUserRank,
                user       = currentUserScore,
                ironManFont = ironManFont
            )
        }

        PaginationControls(
            currentPage = page,
            pageCount   = pageCount,
            ironManFont = ironManFont,
            onPrev      = onPrev,
            onNext      = onNext
        )
    }
}

@Composable
private fun PanelTab(
    panels: List<PanelScore>,
    page: Int,
    pageSize: Int,
    pageCount: Int,
    ironManFont: FontFamily,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val start     = page * pageSize
    val pageItems = panels.drop(start).take(pageSize)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            itemsIndexed(pageItems) { localIdx, panel ->
                LeaderboardRow(
                    rank            = start + localIdx + 1,
                    name            = panel.name,
                    score           = panel.score,
                    profileImageUrl = null,
                    uid             = null,
                    isGwWinner      = false,
                    gwNumber        = null,
                    isCurrentUser   = false,
                    ironManFont     = ironManFont,
                    onClick         = {}
                )
            }
        }
        PaginationControls(
            currentPage = page,
            pageCount   = pageCount,
            ironManFont = ironManFont,
            onPrev      = onPrev,
            onNext      = onNext
        )
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
    isCurrentUser: Boolean,
    ironManFont: FontFamily,
    onClick: () -> Unit
) {
    val borderColor = when {
        isCurrentUser -> Color(0xFF4CAF50)
        rank == 1     -> Gold
        rank == 2     -> Silver
        rank == 3     -> Bronze
        else          -> Color(0xFF2A2A2A)
    }
    val bgColor = when {
        isCurrentUser -> Color(0xFF0A1A0A)
        rank == 1     -> Color(0xFF1A1400)
        else          -> CardBg
    }
    val rankLabel = when (rank) {
        1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = uid != null, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (rank <= 3 || isCurrentUser) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rankLabel,
                color = borderColor,
                fontFamily = ironManFont,
                fontSize = if (rank <= 3) 20.sp else 14.sp,
                modifier = Modifier.width(44.dp)
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, borderColor, CircleShape)
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
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

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = if (isCurrentUser) Color(0xFF4CAF50) else Color.White,
                        fontFamily = ironManFont,
                        fontSize = 15.sp
                    )
                    if (isCurrentUser) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0A1A0A), RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text("YOU", color = Color(0xFF4CAF50), fontFamily = ironManFont, fontSize = 9.sp)
                        }
                    }
                }
                if (isGwWinner && gwNumber != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1A1200), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("⚡ GW$gwNumber", color = Gold, fontFamily = ironManFont, fontSize = 9.sp)
                    }
                }
            }

            Text(
                text = "$score pts",
                color = Gold,
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PinnedYouRow(rank: Int, user: UserScore, ironManFont: FontFamily) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A1A0A))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = Color(0xFF4CAF50),
            fontFamily = ironManFont,
            fontSize = 14.sp,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color(0xFF4CAF50), CircleShape)
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
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
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = Color(0xFF4CAF50), fontFamily = ironManFont, fontSize = 14.sp)
            Text("Your position", color = Dim, fontSize = 10.sp)
        }
        Text("${user.score} pts", color = Gold, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = currentPage > 0) {
            Text("← PREV", color = if (currentPage > 0) Gold else Color(0xFF333333), fontFamily = ironManFont, fontSize = 12.sp)
        }
        Text(
            text = "${currentPage + 1} / $pageCount",
            color = Dim,
            fontFamily = ironManFont,
            fontSize = 13.sp
        )
        TextButton(onClick = onNext, enabled = currentPage < pageCount - 1) {
            Text("NEXT →", color = if (currentPage < pageCount - 1) Gold else Color(0xFF333333), fontFamily = ironManFont, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LeaderboardSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(8) {
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

// Expose tabIndicatorOffset
private fun Modifier.tabIndicatorOffset(tabPosition: androidx.compose.material3.TabPosition): Modifier =
    this.wrapContentSize(Alignment.BottomStart)
        .offset(x = tabPosition.left)
        .width(tabPosition.width)
