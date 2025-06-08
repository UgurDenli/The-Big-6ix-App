package com.invenium.thebig6ix.ui.predictions

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PredictionScreen(
    fixtures: List<FootballFixture>,
    viewModel: PredictionViewModel = viewModel()
) {
    val context = LocalContext.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    val userPredictions by viewModel.userPredictions.collectAsState()

    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L)
        }
    }

    val upcomingFixtures = fixtures.filter { it.homeTeamGoals == -1 && it.awayTeamGoals == -1 }

    var expanded by remember { mutableStateOf(false) }
    var selectedFixture by remember { mutableStateOf<FootballFixture?>(null) }
    var homeGoals by remember { mutableStateOf("") }
    var awayGoals by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.padding(16.dp),
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
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Choose Fixture to Predict",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(
                        selectedFixture?.let { "${it.homeTeam} vs ${it.awayTeam}" } ?: "Select Fixture",
                        color = Color.White
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    upcomingFixtures.forEach { fixture ->
                        DropdownMenuItem(
                            text = { Text("${fixture.homeTeam} vs ${fixture.awayTeam}") },
                            onClick = {
                                selectedFixture = fixture
                                expanded = false
                                homeGoals = ""
                                awayGoals = ""
                            }
                        )
                    }
                }
            }

            selectedFixture?.let { fixture ->
                val deadline = fixture.deadline?.toDate()
                    ?.toInstant()
                    ?.atZone(ZoneId.systemDefault())
                    ?.toLocalDateTime()

                val isExpired = deadline?.isBefore(now) == true
                val countdown = deadline?.let {
                    val d = Duration.between(now, it)
                    if (!d.isNegative && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart())
                    } else null
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (countdown != null) {
                    Text(
                        text = "Deadline in: $countdown",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (isExpired) {
                    Text("Prediction closed", color = Color.Red, fontWeight = FontWeight.Bold)
                    return@let
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = homeGoals,
                        onValueChange = { homeGoals = it },
                        label = { Text("${fixture.homeTeam} Goals") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = awayGoals,
                        onValueChange = { awayGoals = it },
                        label = { Text("${fixture.awayTeam} Goals") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                val alreadyPredicted = userPredictions.any { it.fixtureId == fixture.id }
                if (alreadyPredicted) {
                    Text(
                        text = "Prediction already submitted.",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Button(
                        onClick = {
                            val home = homeGoals.toIntOrNull()
                            val away = awayGoals.toIntOrNull()
                            if (home != null && away != null) {
                                viewModel.submitPredictionIfNotExists(
                                    fixtureId = fixture.id,
                                    homeTeam = fixture.homeTeam,
                                    awayTeam = fixture.awayTeam,
                                    homeGoals = home,
                                    awayGoals = away,
                                    onSuccess = {
                                        Toast.makeText(context, "Prediction submitted!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Enter valid numbers", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = homeGoals.isNotBlank() && awayGoals.isNotBlank()
                    ) {
                        Text("Submit Prediction", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}