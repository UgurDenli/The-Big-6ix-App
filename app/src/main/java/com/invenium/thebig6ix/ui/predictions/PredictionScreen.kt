package com.invenium.thebig6ix.ui.predictions

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.invenium.thebig6ix.data.FootballFixture

@Composable
fun PredictionScreen(
    fixtures: List<FootballFixture>,
    onSubmit: (fixtureId: String, homeGoals: Int, awayGoals: Int) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Predict Scores", style = MaterialTheme.typography.headlineSmall)

        fixtures.filter { it.homeTeamGoals == -1 }.forEach { fixture ->
            var homeGoals by remember { mutableStateOf("") }
            var awayGoals by remember { mutableStateOf("") }

            Spacer(modifier = Modifier.height(16.dp))
            Text("${fixture.homeTeam} vs ${fixture.awayTeam}")

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = homeGoals,
                    onValueChange = { homeGoals = it },
                    label = { Text("${fixture.homeTeam} Goals") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = awayGoals,
                    onValueChange = { awayGoals = it },
                    label = { Text("${fixture.awayTeam} Goals") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            Button(
                onClick = {
                    val home = homeGoals.toIntOrNull()
                    val away = awayGoals.toIntOrNull()
                    if (home != null && away != null) {
                        onSubmit(fixture.id, home, away)
                    } else {
                        Log.d("Prediction", "Invalid input")
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Submit Prediction")
            }
        }
    }
}
