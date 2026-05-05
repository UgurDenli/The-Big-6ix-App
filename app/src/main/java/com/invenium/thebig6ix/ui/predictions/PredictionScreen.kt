package com.invenium.thebig6ix.ui.predictions

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

private val Gold   = Color(0xFFFFD700)
private val CardBg = Color(0xFF111111)
private val Dim    = Color(0xFF888888)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PredictionScreen(viewModel: PredictionViewModel = viewModel()) {
    val context = LocalContext.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    val fixtures           by viewModel.fixtures.collectAsState()
    val userPredictions    by viewModel.userPredictions.collectAsState()
    val mostPickedScoreline by viewModel.mostPickedScoreline.collectAsState()
    val selectedGameweek   by viewModel.selectedGameweek.collectAsState()
    val availableGameweeks by viewModel.availableGameweeks.collectAsState()
    val adminOverride      by viewModel.adminGameweekOverride.collectAsState()

    var titleTapCount    by remember { mutableStateOf(0) }
    var showAdminPanel   by remember { mutableStateOf(false) }
    var adminGameweekInput by remember { mutableStateOf("") }

    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000L) }
    }

    val upcomingFixtures = fixtures.filter { it.homeTeamGoals == -1 && it.awayTeamGoals == -1 }

    val allDeadlinesPassed = fixtures.isNotEmpty() && fixtures.all { fixture ->
        fixture.deadline?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()?.isBefore(now) == true
    }
    val allPredicted = fixtures.isNotEmpty() && fixtures.all { fixture ->
        userPredictions.any { it.fixtureId == fixture.id }
    }
    val showSummary = allDeadlinesPassed || allPredicted

    var selectedFixture by remember { mutableStateOf<FootballFixture?>(null) }
    var homeGoals by remember { mutableStateOf("") }
    var awayGoals by remember { mutableStateOf("") }

    LaunchedEffect(selectedGameweek) {
        selectedFixture = null
        homeGoals = ""
        awayGoals = ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_tbsix),
                contentDescription = null,
                modifier = Modifier.width(260.dp).height(70.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Title — admin 5-tap trigger
            Text(
                text = "PREDICTIONS",
                color = Gold,
                fontFamily = ironManFont,
                fontSize = 28.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.clickable {
                    if (viewModel.isAdmin) {
                        titleTapCount++
                        if (titleTapCount >= 5) { showAdminPanel = !showAdminPanel; titleTapCount = 0 }
                    }
                }
            )

            // Admin panel
            if (showAdminPanel && viewModel.isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Admin Panel", color = Gold, fontFamily = ironManFont, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = adminGameweekInput,
                                onValueChange = { adminGameweekInput = it },
                                label = { Text("Gameweek", color = Dim) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF444444)
                                ),
                                modifier = Modifier.width(120.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.setAdminGameweekOverride(adminGameweekInput.toIntOrNull()) },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("Set", color = Color.Black, fontFamily = ironManFont) }
                        }
                        Row {
                            TextButton(onClick = { viewModel.snapToNext() }) {
                                Text("Snap to Next", color = Gold, fontFamily = ironManFont, fontSize = 13.sp)
                            }
                            TextButton(onClick = { viewModel.setAdminGameweekOverride(null); adminGameweekInput = "" }) {
                                Text("Clear", color = Dim, fontFamily = ironManFont, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gameweek tabs
            if (availableGameweeks.isNotEmpty()) {
                val displayGameweeks = if (adminOverride != null && !availableGameweeks.contains(adminOverride))
                    (availableGameweeks + adminOverride!!).sorted() else availableGameweeks
                val selectedIndex = displayGameweeks.indexOf(selectedGameweek).coerceAtLeast(0)

                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    containerColor = Color(0xFF0D0D0D),
                    contentColor = Gold,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .height(2.dp)
                                    .background(Gold)
                            )
                        }
                    },
                    divider = {}
                ) {
                    displayGameweeks.forEach { gw ->
                        val sel = selectedGameweek == gw
                        Tab(
                            selected = sel,
                            onClick = { viewModel.selectGameweek(gw) },
                            text = {
                                Text(
                                    "GW $gw",
                                    color = if (sel) Gold else Dim,
                                    fontFamily = ironManFont,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Summary view
            if (showSummary) {
                Text(
                    "GW ${selectedGameweek ?: ""} Summary",
                    color = Gold, fontFamily = ironManFont, fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                fixtures.forEach { fixture ->
                    SummaryRow(
                        fixture = fixture,
                        prediction = userPredictions.find { it.fixtureId == fixture.id },
                        ironManFont = ironManFont
                    )
                }

            } else {
                // Fixture cards
                Text(
                    "Choose a Fixture",
                    color = Color.White,
                    fontFamily = ironManFont,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (upcomingFixtures.isEmpty()) {
                    Text("No upcoming fixtures", color = Dim, fontSize = 14.sp)
                }

                upcomingFixtures.forEach { fixture ->
                    val isSelected = selectedFixture?.id == fixture.id
                    val alreadyPredicted = userPredictions.any { it.fixtureId == fixture.id }
                    val deadline = fixture.deadline?.toDate()
                        ?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                    val isExpired = deadline?.isBefore(now) == true

                    FixtureCard(
                        fixture = fixture,
                        isSelected = isSelected,
                        alreadyPredicted = alreadyPredicted,
                        isExpired = isExpired,
                        deadline = deadline,
                        now = now,
                        ironManFont = ironManFont,
                        onClick = {
                            if (!alreadyPredicted && !isExpired) {
                                selectedFixture = if (isSelected) null else fixture
                                homeGoals = ""
                                awayGoals = ""
                            }
                        }
                    )

                    // Inline score entry
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ScoreEntryCard(
                            fixture = fixture,
                            homeGoals = homeGoals,
                            awayGoals = awayGoals,
                            onHomeGoalsChange = { homeGoals = it },
                            onAwayGoalsChange = { awayGoals = it },
                            mostPicked = mostPickedScoreline[fixture.id],
                            ironManFont = ironManFont,
                            onSubmit = {
                                val home = homeGoals.toIntOrNull()
                                val away = awayGoals.toIntOrNull()
                                if (home != null && away != null) {
                                    viewModel.submitPredictionIfNotExists(
                                        fixtureId = fixture.id,
                                        homeTeam = fixture.homeTeam,
                                        awayTeam = fixture.awayTeam,
                                        homeGoals = home,
                                        awayGoals = away,
                                        gameWeek = fixture.gameweek,
                                        onSuccess = {
                                            Toast.makeText(context, "Prediction submitted!", Toast.LENGTH_SHORT).show()
                                            selectedFixture = null
                                        },
                                        onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                    )
                                } else {
                                    Toast.makeText(context, "Enter valid numbers", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel.refreshFixtures() }) {
                Text("Refresh", color = Dim, fontFamily = ironManFont, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun FixtureCard(
    fixture: FootballFixture,
    isSelected: Boolean,
    alreadyPredicted: Boolean,
    isExpired: Boolean,
    deadline: LocalDateTime?,
    now: LocalDateTime,
    ironManFont: FontFamily,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected        -> Gold
        alreadyPredicted  -> Color(0xFF4CAF50)
        isExpired         -> Color(0xFF333333)
        else              -> Color(0xFF2A2A2A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !alreadyPredicted && !isExpired, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fixture.homeTeam, color = Color.White,
                    fontFamily = ironManFont, fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "VS", color = Gold,
                    fontFamily = ironManFont, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    fixture.awayTeam, color = Color.White,
                    fontFamily = ironManFont, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when {
                alreadyPredicted -> Text("✓  Prediction submitted", color = Color(0xFF4CAF50), fontSize = 12.sp)
                isExpired        -> Text("Closed", color = Dim, fontSize = 12.sp)
                deadline != null -> {
                    val d = Duration.between(now, deadline)
                    if (!d.isNegative && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val s = String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart())
                        Text("Closes in  $s", color = Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreEntryCard(
    fixture: FootballFixture,
    homeGoals: String,
    awayGoals: String,
    onHomeGoalsChange: (String) -> Unit,
    onAwayGoalsChange: (String) -> Unit,
    mostPicked: Pair<Int, Int>?,
    ironManFont: FontFamily,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Your Prediction", color = Dim, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Home team input
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        fixture.homeTeam, color = Gold,
                        fontFamily = ironManFont, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = homeGoals,
                        onValueChange = {
                            if (it.length <= 2 && (it.toIntOrNull() ?: 0) in 0..99) onHomeGoalsChange(it)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 32.sp, textAlign = TextAlign.Center, color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Gold
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(76.dp)
                    )
                }

                Text(
                    "—", color = Color(0xFF444444), fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Away team input
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        fixture.awayTeam, color = Gold,
                        fontFamily = ironManFont, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = awayGoals,
                        onValueChange = {
                            if (it.length <= 2 && (it.toIntOrNull() ?: 0) in 0..99) onAwayGoalsChange(it)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 32.sp, textAlign = TextAlign.Center, color = Color.White
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Gold
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(76.dp)
                    )
                }
            }

            mostPicked?.let { (h, a) ->
                Spacer(modifier = Modifier.height(10.dp))
                Text("Most picked:  $h – $a", color = Color(0xFF555555), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = homeGoals.isNotBlank() && awayGoals.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    disabledContainerColor = Color(0xFF333333)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Submit Prediction",
                    color = Color.Black,
                    fontFamily = ironManFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Just like the show, submitted predictions are final",
                color = Color(0xFF444444), fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SummaryRow(
    fixture: FootballFixture,
    prediction: PredictionViewModel.UserPrediction?,
    ironManFont: FontFamily
) {
    val pointColor = when {
        prediction == null -> Color(0xFFF44336)
        fixture.homeTeamGoals == -1 -> Color(0xFF888888)
        prediction.homeGoals == fixture.homeTeamGoals && prediction.awayGoals == fixture.awayTeamGoals -> Color(0xFF4CAF50)
        else -> {
            val predictedResult = when {
                prediction.homeGoals > prediction.awayGoals -> "home"
                prediction.awayGoals > prediction.homeGoals -> "away"
                else -> "draw"
            }
            if (predictedResult == fixture.winner) Color(0xFFFFA500) else Color(0xFFF44336)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                fixture.homeTeam, color = Color.White,
                fontFamily = ironManFont, fontSize = 13.sp, modifier = Modifier.weight(1f)
            )
            Text(
                text = if (prediction != null) "${prediction.homeGoals}  :  ${prediction.awayGoals}" else "—  :  —",
                color = pointColor, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                fixture.awayTeam, color = Color.White,
                fontFamily = ironManFont, fontSize = 13.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End
            )
        }
    }
}
