package com.invenium.thebig6ix.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastPredictionsScreen() {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser ?: return
    val db = FirebaseFirestore.getInstance()
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var selectedGameweek by remember { mutableStateOf("All") }
    var expanded by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf(emptyList<String>()) }
    val allGameweeks = remember { listOf("All") + (0..20).map { "Gameweek $it" } }

    LaunchedEffect(selectedGameweek) {
        val query = db.collection("predictions").whereEqualTo("userId", user.uid)
        val filteredQuery = if (selectedGameweek != "All") {
            query.whereEqualTo("gameweek", selectedGameweek.replace("Gameweek ", "").toIntOrNull())
        } else query

        val result = filteredQuery.get().await()
        predictions = result.documents.map {
            val home = it.getLong("homeTeamGoals") ?: 0
            val away = it.getLong("awayTeamGoals") ?: 0
            val homeTeam = it.getString("homeTeam") ?: "Home"
            val awayTeam = it.getString("awayTeam") ?: "Away"
            "$homeTeam $home - $away $awayTeam"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Past Predictions",
                color = Color(0xFFFFD700),
                fontFamily = ironManFont,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gameweek Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedGameweek,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Gameweek", color = Color(0xFFFFD700)) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .background(Color.Black),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black,
                        disabledContainerColor = Color.Black,
                        focusedBorderColor = Color(0xFFFFD700),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFFFD700),
                        unfocusedLabelColor = Color.LightGray
                    )
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color.Black)
                ) {
                    allGameweeks.forEach { week ->
                        DropdownMenuItem(
                            text = { Text(week, color = Color.White) },
                            onClick = {
                                selectedGameweek = week
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            predictions.forEach {
                Text(it, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
