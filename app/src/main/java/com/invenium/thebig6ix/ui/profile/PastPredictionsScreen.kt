package com.invenium.thebig6ix.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastPredictionsScreen(userId: String? = null) {
    val auth = FirebaseAuth.getInstance()
    val resolvedUserId = userId ?: auth.currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var selectedGameweek by remember { mutableStateOf("All") }
    var expanded by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf(emptyList<Triple<String, String, String>>()) }
    val allGameweeks = remember { listOf("All") + (1..38).map { "Gameweek $it" } }

    LaunchedEffect(selectedGameweek, resolvedUserId) {
        val query = db.collection("predictions").whereEqualTo("userId", resolvedUserId)
        val filteredQuery = if (selectedGameweek != "All") {
            query.whereEqualTo("gameweek", selectedGameweek.replace("Gameweek ", "").toIntOrNull())
        } else query

        val result = filteredQuery.get().await()
        predictions = result.documents.map {
            val home = it.getLong("homeTeamGoals") ?: 0
            val away = it.getLong("awayTeamGoals") ?: 0
            Triple(
                it.getString("homeTeam") ?: "Home",
                "$home : $away",
                it.getString("awayTeam") ?: "Away"
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Past Predictions",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedGameweek,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Gameweek", color = Color(0xFFFFD700)) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black,
                        focusedBorderColor = Color(0xFFFFD700), unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFFFD700), unfocusedLabelColor = Color.LightGray
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = androidx.compose.ui.Modifier.background(Color.Black)
                ) {
                    allGameweeks.forEach { week ->
                        DropdownMenuItem(
                            text = { Text(week, color = Color.White) },
                            onClick = { selectedGameweek = week; expanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (predictions.isEmpty()) {
                Text("No predictions found.", color = Color.Gray, fontSize = 14.sp)
            } else {
                predictions.forEach { (home, score, away) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(BorderStroke(1.dp, Color(0xFFFFD700))),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(home, color = Color(0xFFFFD700), fontFamily = ironManFont, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(score, color = Color.White, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(away, color = Color(0xFFFFD700), fontFamily = ironManFont, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}
