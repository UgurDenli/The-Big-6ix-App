package com.invenium.thebig6ix.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.invenium.thebig6ix.data.FootballFixture

@Composable
fun HomeScreen(
    fixtures: List<FootballFixture>,
    userPoints: Int
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome Back!", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your Points: $userPoints", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(16.dp))
        Text("Upcoming Fixtures", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(fixtures.filter { it.homeTeamGoals == -1 }) { fixture ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${fixture.homeTeam} vs ${fixture.awayTeam}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = fixture.date, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
