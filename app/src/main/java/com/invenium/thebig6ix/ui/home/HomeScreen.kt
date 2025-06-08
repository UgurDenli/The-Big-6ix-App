package com.invenium.thebig6ix.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.FootballFixture

@Composable
fun HomeScreen(
    userPoints: Int,
    viewModel: HomeViewModel = viewModel()
) {
    val fixtures by viewModel.fixtures.collectAsState()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Top,
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
                text = "Your Points: $userPoints",
                color = Color.White,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Upcoming Fixtures",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(fixtures.filter { it.homeTeamGoals == -1 && it.awayTeamGoals == -1 }) { fixture ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("${fixture.homeTeam} vs ${fixture.awayTeam}", color = Color.White)
                            Text(fixture.date, color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}