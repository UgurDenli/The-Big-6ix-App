package com.invenium.thebig6ix.ui.leaderboard

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.ui.leaderboard.FilterType.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel = viewModel()) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val users by viewModel.users.collectAsState()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val refreshScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    fun refresh() {
        isRefreshing = true
        refreshScope.launch {
            viewModel.setFilter(selectedFilter)
            isRefreshing = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tbsix),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(top = 32.dp)
                    .width(300.dp)
                    .height(80.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The Big 6ix",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton("Weekly", WEEKLY, selectedFilter, viewModel, ironManFont)
                FilterButton("Monthly", MONTHLY, selectedFilter, viewModel, ironManFont)
                FilterButton("All Time", ALL_TIME, selectedFilter, viewModel, ironManFont)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pull-to-refresh simulation
            Button(
                onClick = { refresh() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Refresh", fontFamily = ironManFont, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(users) { index, user ->
                    val isCurrentUser = user.uid == currentUserId
                    val animatedScore by animateIntAsState(user.score)

                    val borderColor = when (index) {
                        0 -> Color(0xFFFFD700)
                        1 -> Color(0xFFC0C0C0)
                        2 -> Color(0xFFCD7F32)
                        else -> if (isCurrentUser) Color(0xFF1E88E5) else Color.Transparent
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(BorderStroke(2.dp, borderColor)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontFamily = ironManFont,
                                fontSize = 18.sp,
                                modifier = Modifier.width(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = user.name,
                                    color = Color.White,
                                    fontFamily = ironManFont,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Points: $animatedScore",
                                    color = Color.LightGray,
                                    fontFamily = ironManFont,
                                    fontSize = 14.sp
                                )
                            }

                            val trendIcon = when {
                                user.trend > 0 -> R.drawable.ic_arrow_up
                                user.trend < 0 -> R.drawable.ic_arrow_down
                                else -> null
                            }
                            trendIcon?.let {
                                Icon(
                                    painter = painterResource(id = it),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    filter: FilterType,
    selectedFilter: FilterType,
    viewModel: LeaderboardViewModel,
    font: FontFamily
) {
    val isSelected = selectedFilter == filter
    val bgColor = if (isSelected) Color(0xFFFFD700) else Color.White
    val contentColor = Color.Black

    Button(
        onClick = { viewModel.setFilter(filter) },
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = contentColor)
    ) {
        Text(label, fontFamily = font, fontWeight = FontWeight.Bold)
    }
}
