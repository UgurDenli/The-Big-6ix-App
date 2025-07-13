package com.invenium.thebig6ix.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.invenium.thebig6ix.R
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val leaderboard = viewModel.leaderboard.collectAsState().value
    val selectedTab = remember { mutableStateOf(0) }
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_tbsix),
                contentDescription = null,
                modifier = Modifier.padding(top = 32.dp).width(300.dp).height(80.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The Big 6ix",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The Top 5 Players in the League.",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            TabRow(selectedTabIndex = selectedTab.value, containerColor = Color.DarkGray) {
                listOf("Weekly", "Monthly", "Total").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab.value == index,
                        onClick = { selectedTab.value = index },
                        text = {
                            Text(
                                text = label,
                                color = if (selectedTab.value == index) Color.Yellow else Color.White,
                                fontFamily = ironManFont
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val navController = rememberNavController()
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(leaderboard.take(5)) { index, user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "#${index + 1} ${user.name}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                if (user.isNewLeader) {
                                    Text("New Leader", color = Color.Cyan, fontSize = 12.sp)
                                }
                                Text(
                                    text = when (selectedTab.value) {
                                        0 -> "${user.weeklyScore} pts"
                                        1 -> "${user.monthlyScore} pts"
                                        else -> "${user.totalScore} pts"
                                    },
                                    color = Color.Yellow,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {viewModel.refreshFixtures()},
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = "Refresh Leaderboard",
                    color = Color.Black,
                    fontFamily = ironManFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}
