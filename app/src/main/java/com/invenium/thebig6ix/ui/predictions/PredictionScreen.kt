package com.invenium.thebig6ix.ui.predictions

import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.FootballFixture
import com.invenium.thebig6ix.data.flagFor
import com.invenium.thebig6ix.ui.admin.AdminPanelSection
import com.invenium.thebig6ix.ui.home.ShimmerBox
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

private val Gold          = Color(0xFFFFD700)
private val CardBg        = Color(0xFF111111)
private val Dim           = Color(0xFF888888)
private val WildcardColor = Color(0xFF9C6ADE)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PredictionScreen(viewModel: PredictionViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    val fixtures           by viewModel.fixtures.collectAsState()
    val userPredictions    by viewModel.userPredictions.collectAsState()
    val mostPickedScoreline by viewModel.mostPickedScoreline.collectAsState()
    val selectedGameweek   by viewModel.selectedGameweek.collectAsState()
    val availableGameweeks by viewModel.availableGameweeks.collectAsState()
    val adminOverride      by viewModel.adminGameweekOverride.collectAsState()
    val wildcardAvailable   by viewModel.wildcardAvailable.collectAsState()
    val doubleDownAvailable by viewModel.doubleDownAvailable.collectAsState()
    val doubleDownUsedGameweek by viewModel.doubleDownUsedGameweek.collectAsState()
    val captainAvailable    by viewModel.captainAvailable.collectAsState()
    val isLoading          by viewModel.isLoading.collectAsState()

    var titleTapCount      by remember { mutableStateOf(0) }
    var showAdminPanel     by remember { mutableStateOf(false) }
    var adminGameweekInput by remember { mutableStateOf("") }
    var wildcardFixture    by remember { mutableStateOf<FootballFixture?>(null) }
    var wildcardHomeInt    by remember { mutableStateOf(0) }
    var wildcardAwayInt    by remember { mutableStateOf(0) }
    var showWildcardConfirm by remember { mutableStateOf(false) }
    var showSuccessAnim    by remember { mutableStateOf(false) }

    // Token activation dialog states
    var showDoubleDownDialog     by remember { mutableStateOf(false) }
    var showCaptainPickerDialog  by remember { mutableStateOf(false) }
    var showWildcardPickerDialog by remember { mutableStateOf(false) }
    var wildcardDialogFixture    by remember { mutableStateOf<FootballFixture?>(null) }
    var wildcardDialogHomeInt    by remember { mutableStateOf(0) }
    var wildcardDialogAwayInt    by remember { mutableStateOf(0) }

    LaunchedEffect(showSuccessAnim) {
        if (showSuccessAnim) {
            delay(1500)
            showSuccessAnim = false
        }
    }

    if (showWildcardConfirm) {
        AlertDialog(
            onDismissRequest = { showWildcardConfirm = false },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = WildcardColor,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Use Wildcard?", fontFamily = ironManFont) },
            text = { Text("This is your one wildcard for the season. Your prediction will be updated and you won't be able to use the wildcard again.", fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showWildcardConfirm = false
                    val fixture = wildcardFixture ?: return@TextButton
                    viewModel.submitWildcard(
                        fixtureId = fixture.id,
                        homeGoals = wildcardHomeInt,
                        awayGoals = wildcardAwayInt,
                        gameWeek = selectedGameweek ?: fixture.gameweek,
                        onSuccess = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "🃏 Wildcard used!", Toast.LENGTH_SHORT).show()
                            wildcardFixture = null
                        },
                        onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("Confirm", color = WildcardColor, fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWildcardConfirm = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Double Down activation dialog ──────────────────────────────────────
    if (showDoubleDownDialog) {
        AlertDialog(
            onDismissRequest = { showDoubleDownDialog = false },
            containerColor = Color(0xFF1A1400),
            titleContentColor = Gold,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("⚡ Activate Double Down?", fontFamily = ironManFont) },
            text = {
                Column {
                    Text(
                        "All correct predictions in GW${selectedGameweek ?: "?"} will earn 2× points this gameweek.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This is a one-time use token and cannot be undone.",
                        fontSize = 12.sp,
                        color = Color(0xFF888888),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDoubleDownDialog = false
                    viewModel.submitDoubleDown(
                        gameweek = selectedGameweek ?: 0,
                        onSuccess = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(
                                context,
                                "⚡ Double Down activated for GW${selectedGameweek}!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    )
                }) {
                    Text("Activate", color = Gold, fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoubleDownDialog = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Captain fixture picker dialog ──────────────────────────────────────
    if (showCaptainPickerDialog) {
        val captainEligible = fixtures.filter { fixture ->
            val pred = userPredictions.find { it.fixtureId == fixture.id }
            val fixtureDeadline = fixture.deadline?.toDate()?.toInstant()
                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            pred != null && !pred.captainUsed &&
                fixtureDeadline?.isAfter(LocalDateTime.now()) == true &&
                fixture.homeTeamGoals < 0
        }
        AlertDialog(
            onDismissRequest = { showCaptainPickerDialog = false },
            containerColor = Color(0xFF0D1A2E),
            titleContentColor = Color(0xFF4B9EFF),
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("🎖️ Choose Captain Fixture", fontFamily = ironManFont) },
            text = {
                Column {
                    if (captainEligible.isEmpty()) {
                        Text(
                            "Submit a prediction first, then come back to set your captain.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            "Your captain earns 2× points if your score prediction is correct.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            captainEligible.forEach { fixture ->
                                val pred = userPredictions.find { it.fixtureId == fixture.id }!!
                                TextButton(
                                    onClick = {
                                        showCaptainPickerDialog = false
                                        viewModel.setCaptain(
                                            fixtureId = fixture.id,
                                            onSuccess = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                Toast.makeText(
                                                    context,
                                                    "🎖️ Captain set on ${fixture.homeTeam} vs ${fixture.awayTeam}!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onFailure = {
                                                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${fixture.homeTeam} vs ${fixture.awayTeam}",
                                                color = Color.White,
                                                fontFamily = ironManFont,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${pred.homeGoals} – ${pred.awayGoals}",
                                            color = Color(0xFF4B9EFF),
                                            fontFamily = ironManFont,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF1A2A3A), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCaptainPickerDialog = false }) {
                    Text("Close", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Wildcard fixture picker dialog (step 1) ────────────────────────────
    if (showWildcardPickerDialog) {
        val wildcardEligible = fixtures.filter { fixture ->
            val pred = userPredictions.find { it.fixtureId == fixture.id }
            val fixtureDeadline = fixture.deadline?.toDate()?.toInstant()
                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            pred != null && !pred.wildcardUsed &&
                fixtureDeadline?.isAfter(LocalDateTime.now()) == true &&
                fixture.homeTeamGoals == -1
        }
        AlertDialog(
            onDismissRequest = { showWildcardPickerDialog = false },
            containerColor = Color(0xFF160D20),
            titleContentColor = WildcardColor,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("🃏 Use Wildcard", fontFamily = ironManFont) },
            text = {
                Column {
                    if (wildcardEligible.isEmpty()) {
                        Text(
                            "No eligible predictions to wildcard. Submit a prediction first, or the match deadline may have passed.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        Text(
                            "Choose a prediction to change (one use per season):",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            wildcardEligible.forEach { fixture ->
                                val pred = userPredictions.find { it.fixtureId == fixture.id }!!
                                TextButton(
                                    onClick = {
                                        showWildcardPickerDialog = false
                                        wildcardDialogFixture = fixture
                                        wildcardDialogHomeInt = pred.homeGoals
                                        wildcardDialogAwayInt = pred.awayGoals
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${fixture.homeTeam} vs ${fixture.awayTeam}",
                                                color = Color.White,
                                                fontFamily = ironManFont,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${pred.homeGoals} – ${pred.awayGoals}",
                                            color = WildcardColor,
                                            fontFamily = ironManFont,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color(0xFF2A1A3A), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWildcardPickerDialog = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Wildcard score entry dialog (step 2) ───────────────────────────────
    wildcardDialogFixture?.let { dlgFixture ->
        AlertDialog(
            onDismissRequest = { wildcardDialogFixture = null },
            containerColor = Color(0xFF160D20),
            titleContentColor = WildcardColor,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("🃏 Change Prediction", fontFamily = ironManFont) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${dlgFixture.homeTeam}  vs  ${dlgFixture.awayTeam}",
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    // Home stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            dlgFixture.homeTeam,
                            color = WildcardColor,
                            fontFamily = ironManFont,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GoalStepper(
                            value = wildcardDialogHomeInt,
                            onDecrement = { if (wildcardDialogHomeInt > 0) wildcardDialogHomeInt-- },
                            onIncrement = { wildcardDialogHomeInt++ },
                            accentColor = WildcardColor,
                            ironManFont = ironManFont,
                            buttonSize = 36.dp,
                            valueFontSize = 30.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Away stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            dlgFixture.awayTeam,
                            color = WildcardColor,
                            fontFamily = ironManFont,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        GoalStepper(
                            value = wildcardDialogAwayInt,
                            onDecrement = { if (wildcardDialogAwayInt > 0) wildcardDialogAwayInt-- },
                            onIncrement = { wildcardDialogAwayInt++ },
                            accentColor = WildcardColor,
                            ironManFont = ironManFont,
                            buttonSize = 36.dp,
                            valueFontSize = 30.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "One use per season — cannot be undone",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Bridge to the existing confirmation dialog
                    wildcardFixture = dlgFixture
                    wildcardHomeInt = wildcardDialogHomeInt
                    wildcardAwayInt = wildcardDialogAwayInt
                    wildcardDialogFixture = null
                    showWildcardConfirm = true
                }) {
                    Text("Continue →", color = WildcardColor, fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { wildcardDialogFixture = null }) {
                    Text("Back", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000L) }
    }

    // Show ALL fixtures in the selected GW — scored games stay visible as expired
    // so a single early match kicking off doesn't make the card disappear.
    val upcomingFixtures = fixtures

    // Show summary once ALL fixtures in the GW have passed their individual deadline.
    val allDeadlinesPassed = fixtures.isNotEmpty() && fixtures.all { f ->
        val dl = f.deadline?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
        (dl != null && dl.isBefore(now)) || f.homeTeamGoals >= 0
    }
    val allPredicted = fixtures.isNotEmpty() && fixtures.all { fixture ->
        userPredictions.any { it.fixtureId == fixture.id }
    }
    val showSummary = allDeadlinesPassed || allPredicted

    var selectedFixture by remember { mutableStateOf<FootballFixture?>(null) }
    var homeGoals by remember { mutableStateOf(0) }
    var awayGoals by remember { mutableStateOf(0) }

    // Tracks which fixture IDs have been animated in (persists across recompositions)
    var animatedCardIds by remember { mutableStateOf(emptySet<String>()) }

    // Single effect keyed on BOTH gameweek AND fixture list.
    // Previously two separate LaunchedEffects had a race condition: the selectedGameweek
    // effect would reset animatedCardIds mid-animation, wiping the first fixture (Brazil)
    // which was added at delay(0) before the reset ran.  By combining into one effect,
    // the reset and the staggered animation are always atomic.
    LaunchedEffect(selectedGameweek, upcomingFixtures) {
        // Reset form state when GW changes (harmless no-op when only fixtures change)
        selectedFixture = null
        homeGoals = 0
        awayGoals = 0
        wildcardFixture = null
        wildcardHomeInt = 0
        wildcardAwayInt = 0
        wildcardDialogFixture = null
        wildcardDialogHomeInt = 0
        wildcardDialogAwayInt = 0

        // Determine which cards still need to animate in
        val toAnimate = upcomingFixtures.filter { it.id !in animatedCardIds }
        if (toAnimate.isEmpty()) return@LaunchedEffect

        // If this is a fresh GW (animatedCardIds is empty) stagger all cards;
        // otherwise just slide in the new ones (e.g. Brazil arriving from server)
        toAnimate.forEachIndexed { index, fixture ->
            delay(index * 75L)
            animatedCardIds = animatedCardIds + fixture.id
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        if (isLoading && fixtures.isEmpty()) {
            PredictionSkeletonScreen(ironManFont = ironManFont)
        } else {
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
                    AdminPanelSection(
                        currentGameweek          = selectedGameweek,
                        adminGameweekInput       = adminGameweekInput,
                        onAdminGameweekInputChange = { adminGameweekInput = it },
                        onSetOverride            = { viewModel.setAdminGameweekOverride(adminGameweekInput.toIntOrNull()) },
                        onSnapToNext             = { viewModel.snapToNext() },
                        onClearOverride          = { viewModel.setAdminGameweekOverride(null); adminGameweekInput = "" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gameweek tabs — "THIS GW" / "NEXT GW" when two GWs are available
                if (availableGameweeks.isNotEmpty()) {
                    val displayGameweeks = if (adminOverride != null && !availableGameweeks.contains(adminOverride))
                        (availableGameweeks + adminOverride!!).sorted() else availableGameweeks
                    val selectedIndex = displayGameweeks.indexOf(selectedGameweek).coerceAtLeast(0)
                    val isTwoGwMode = displayGameweeks.size == 2

                    TabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = Color(0xFF0D0D0D),
                        contentColor = Gold,
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
                        displayGameweeks.forEachIndexed { idx, gw ->
                            val sel = selectedGameweek == gw
                            if (isTwoGwMode) {
                                val label = if (idx == 0) "THIS GW" else "NEXT GW"
                                Tab(
                                    selected = sel,
                                    onClick = { viewModel.selectGameweek(gw) },
                                    selectedContentColor = Gold,
                                    unselectedContentColor = Dim
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            label,
                                            color = if (sel) Gold else Dim,
                                            fontFamily = ironManFont,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            "GW $gw",
                                            color = if (sel) Gold.copy(alpha = 0.6f) else Dim.copy(alpha = 0.5f),
                                            fontFamily = ironManFont,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            } else {
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
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Token row — IntrinsicSize.Max keeps all three cards the same height
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TokenCard(
                        emoji = "🃏",
                        name = "WILDCARD",
                        description = "Change a locked prediction",
                        color = WildcardColor,
                        available = wildcardAvailable,
                        ironManFont = ironManFont,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = if (wildcardAvailable) {{ showWildcardPickerDialog = true }} else null
                    )
                    TokenCard(
                        emoji = "⚡",
                        name = "DOUBLE DOWN",
                        description = "2× points one gameweek",
                        color = Gold,
                        available = doubleDownAvailable,
                        ironManFont = ironManFont,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = if (doubleDownAvailable) {{ showDoubleDownDialog = true }} else null,
                        // Once used, surface which GW the 2× is locked into. Highlight when
                        // viewing that GW, otherwise just note the gameweek it was spent on.
                        activeBadge = doubleDownUsedGameweek?.let { ddGw ->
                            if (ddGw == selectedGameweek) "⚡ ACTIVE THIS GW" else "USED · GW$ddGw"
                        },
                        activeHighlight = doubleDownUsedGameweek != null && doubleDownUsedGameweek == selectedGameweek
                    )
                    TokenCard(
                        emoji = "🎖️",
                        name = "CAPTAIN",
                        description = "2× points on one fixture",
                        color = Color(0xFF4B9EFF),
                        available = captainAvailable,
                        ironManFont = ironManFont,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = if (captainAvailable) {{ showCaptainPickerDialog = true }} else null
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Summary view
                if (showSummary) {
                    Text(
                        "GW ${selectedGameweek ?: ""} Summary",
                        color = Gold, fontFamily = ironManFont, fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    fixtures.forEach { fixture ->
                        val prediction = userPredictions.find { it.fixtureId == fixture.id }
                        val canWildcard = wildcardAvailable &&
                            fixture.homeTeamGoals == -1 &&
                            prediction != null &&
                            prediction.wildcardUsed.not()
                        val isWildcardActive = wildcardFixture?.id == fixture.id

                        SummaryRow(
                            fixture = fixture,
                            prediction = prediction,
                            canWildcard = canWildcard,
                            isWildcardActive = isWildcardActive,
                            ironManFont = ironManFont,
                            onWildcardTap = {
                                wildcardFixture = if (isWildcardActive) null else fixture
                                wildcardHomeInt = prediction?.homeGoals ?: 0
                                wildcardAwayInt = prediction?.awayGoals ?: 0
                            }
                        )

                        if (isWildcardActive) {
                            Spacer(modifier = Modifier.height(4.dp))
                            WildcardEntryCard(
                                fixture = fixture,
                                homeGoals = wildcardHomeInt,
                                awayGoals = wildcardAwayInt,
                                onHomeGoalsChange = { wildcardHomeInt = it },
                                onAwayGoalsChange = { wildcardAwayInt = it },
                                ironManFont = ironManFont,
                                onSubmit = { showWildcardConfirm = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                } else {
                    // Fixture cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF222222))
                        Text(
                            "  CHOOSE A FIXTURE  ",
                            color = Dim,
                            fontFamily = ironManFont,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF222222))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Only consider still-predictable (unscored) fixtures for the "all predicted" banner
                    val predictableFixtures = upcomingFixtures.filter { it.homeTeamGoals == -1 }
                    val allUpcomingPredicted = predictableFixtures.isNotEmpty() &&
                        predictableFixtures.all { f -> userPredictions.any { it.fixtureId == f.id } }

                    if (upcomingFixtures.isEmpty() || allUpcomingPredicted) {
                        AllPredictedCard(
                            gwNumber = selectedGameweek,
                            ironManFont = ironManFont
                        )
                    }

                    upcomingFixtures.forEach { fixture ->
                        if (allUpcomingPredicted) return@forEach

                        val isCardVisible = fixture.id in animatedCardIds
                        val isSelected = selectedFixture?.id == fixture.id
                        val existingPred = userPredictions.find { it.fixtureId == fixture.id }
                        val alreadyPredicted = existingPred != null
                        val isCaptained = existingPred?.captainUsed == true
                        val fixtureDeadlineLocal = fixture.deadline?.toDate()?.toInstant()
                            ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                        val isExpired = fixtureDeadlineLocal?.isBefore(now) == true || fixture.homeTeamGoals >= 0
                        val canCaptain = alreadyPredicted && !isExpired && captainAvailable && !isCaptained

                        AnimatedVisibility(
                            visible = isCardVisible,
                            enter = slideInVertically(
                                initialOffsetY = { it / 2 },
                                animationSpec  = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessLow
                                )
                            ) + fadeIn(animationSpec = tween(220))
                        ) {
                            Column {
                                FixtureCard(
                                    fixture = fixture,
                                    isSelected = isSelected,
                                    alreadyPredicted = alreadyPredicted,
                                    isExpired = isExpired,
                                    isCaptained = isCaptained,
                                    canCaptain = canCaptain,
                                    deadline = fixtureDeadlineLocal,
                                    now = now,
                                    ironManFont = ironManFont,
                                    existingPrediction = existingPred,
                                    onClick = {
                                        if (!alreadyPredicted && !isExpired) {
                                            selectedFixture = if (isSelected) null else fixture
                                            homeGoals = 0
                                            awayGoals = 0
                                        }
                                    },
                                    onCaptainTap = {
                                        viewModel.setCaptain(
                                            fixtureId = fixture.id,
                                            onSuccess = { Toast.makeText(context, "🎖️ Captain set!", Toast.LENGTH_SHORT).show() },
                                            onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                        )
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
                                            viewModel.submitPredictionIfNotExists(
                                                fixtureId = fixture.id,
                                                homeTeam = fixture.homeTeam,
                                                awayTeam = fixture.awayTeam,
                                                homeGoals = homeGoals,
                                                awayGoals = awayGoals,
                                                gameWeek = selectedGameweek ?: fixture.gameweek,
                                                onSuccess = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showSuccessAnim = true
                                                    selectedFixture = null
                                                    homeGoals = 0
                                                    awayGoals = 0
                                                },
                                                onFailure = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                            )
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.refreshFixtures() }) {
                    Text("Refresh", color = Dim, fontFamily = ironManFont, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success animation overlay
            if (showSuccessAnim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (showSuccessAnim) 1f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "success_scale"
                    )
                    Card(
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F0A)),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(2.dp, Color(0xFF4CAF50))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("✓", color = Color(0xFF4CAF50), fontSize = 56.sp)
                            Text("LOCKED IN", color = Color(0xFF4CAF50), fontFamily = ironManFont, fontSize = 13.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionSkeletonScreen(ironManFont: FontFamily) {
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

        Text(
            text = "PREDICTIONS",
            color = Gold,
            fontFamily = ironManFont,
            fontSize = 28.sp,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Shimmer GW tab
        ShimmerBox(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)))

        Spacer(modifier = Modifier.height(20.dp))

        // Shimmer token cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerBox(Modifier.weight(1f).height(90.dp).clip(RoundedCornerShape(10.dp)))
            ShimmerBox(Modifier.weight(1f).height(90.dp).clip(RoundedCornerShape(10.dp)))
            ShimmerBox(Modifier.weight(1f).height(90.dp).clip(RoundedCornerShape(10.dp)))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Shimmer divider header
        ShimmerBox(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)))

        Spacer(modifier = Modifier.height(12.dp))

        // Shimmer fixture cards
        repeat(4) {
            ShimmerBox(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AllPredictedCard(gwNumber: Int?, ironManFont: FontFamily) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F0A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✓", color = Color(0xFF4CAF50), fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "YOU'RE ALL SET",
                color = Color(0xFF4CAF50),
                fontFamily = ironManFont,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (gwNumber != null) "All GW$gwNumber predictions submitted" else "All predictions submitted",
                color = Color(0xFF888888),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Check back after the matches to see your score",
                color = Color(0xFF555555),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NameLabel(team: String, ironManFont: FontFamily, modifier: Modifier = Modifier) {
    val flag = flagFor(team)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (flag.isNotEmpty()) {
            Text(flag, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            team, color = Gold,
            fontFamily = ironManFont, fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun GoalStepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    accentColor: Color,
    ironManFont: FontFamily,
    buttonSize: Dp = 44.dp,
    valueFontSize: TextUnit = 44.sp
) {
    val innerIconSize = (buttonSize.value * 0.55f).sp
    val spacing       = if (buttonSize >= 40.dp) 16.dp else 10.dp
    val valueWidth    = if (buttonSize >= 40.dp) 52.dp else 40.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Decrement button
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(
                    if (value > 0) accentColor.copy(alpha = 0.12f) else Color(0xFF1A1A1A),
                    RoundedCornerShape(10.dp)
                )
                .border(1.dp, if (value > 0) accentColor.copy(alpha = 0.4f) else Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                .clickable(enabled = value > 0, onClick = onDecrement),
            contentAlignment = Alignment.Center
        ) {
            Text("−", color = if (value > 0) accentColor else Color(0xFF333333), fontSize = innerIconSize, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(spacing))

        // Value display
        Text(
            text = "$value",
            color = accentColor,
            fontFamily = ironManFont,
            fontWeight = FontWeight.Bold,
            fontSize = valueFontSize,
            modifier = Modifier.width(valueWidth),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.width(spacing))

        // Increment button
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .clickable(onClick = onIncrement),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = accentColor, fontSize = innerIconSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TokenCard(
    emoji: String,
    name: String,
    description: String,
    color: Color,
    available: Boolean,
    ironManFont: FontFamily,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    // When a spent token should still show context (e.g. Double Down's gameweek),
    // pass a label here. activeHighlight makes the card glow in the token's colour
    // instead of the dim "used" styling.
    activeBadge: String? = null,
    activeHighlight: Boolean = false
) {
    val lit       = available || activeHighlight
    val bg        = if (lit) color.copy(alpha = 0.08f) else Color(0xFF0D0D0D)
    val border    = if (lit) color.copy(alpha = 0.45f) else Color(0xFF222222)
    val textColor = if (lit) color else Dim
    val tappable  = available && onClick != null

    Card(
        modifier = modifier.let { m -> if (tappable) m.clickable(onClick = onClick!!) else m },
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (tappable || activeHighlight) 1.5.dp else 1.dp, border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                name,
                color = textColor,
                fontFamily = ironManFont,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                description,
                color = if (lit) Color(0xFF999999) else Color(0xFF444444),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (lit) color.copy(alpha = 0.15f) else Color(0xFF1A1A1A),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    when {
                        activeBadge != null -> activeBadge
                        tappable            -> "TAP TO USE"
                        available           -> "AVAILABLE"
                        else                -> "USED"
                    },
                    color = textColor,
                    fontFamily = ironManFont,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp
                )
            }
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
    isCaptained: Boolean = false,
    canCaptain: Boolean = false,
    deadline: LocalDateTime?,
    now: LocalDateTime,
    ironManFont: FontFamily,
    existingPrediction: PredictionViewModel.UserPrediction? = null,
    onClick: () -> Unit,
    onCaptainTap: () -> Unit = {}
) {
    // ── Deadline urgency ─────────────────────────────────────────────────────
    val deadlineDuration = if (deadline != null) Duration.between(now, deadline) else null
    val totalDeadlineMins = deadlineDuration?.let { if (it.isNegative) 0L else it.toMinutes() } ?: Long.MAX_VALUE
    val isCritical = totalDeadlineMins < 10
    val isUrgent   = totalDeadlineMins < 60

    // Infinite pulse for critical (< 10 min) — always declared, only used conditionally
    val pulseTransition = rememberInfiniteTransition(label = "deadline_pulse")
    val rawPulse by pulseTransition.animateFloat(
        initialValue  = 0.45f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseAlpha = if (isCritical && !alreadyPredicted && !isExpired) rawPulse else 1f

    val urgencyDotColor = when {
        isCritical  -> Color(0xFFF44336)
        isUrgent    -> Color(0xFFFF8C00)
        else        -> Gold.copy(alpha = 0.7f)
    }
    val urgencyTextColor = when {
        isCritical  -> Color(0xFFF44336)
        isUrgent    -> Color(0xFFFF8C00)
        else        -> Dim
    }
    val ctaLabel = when {
        isCritical  -> "CLOSES SOON!"
        isUrgent    -> "CLOSING SOON"
        else        -> "TAP TO PREDICT"
    }
    val ctaColor = when {
        isCritical  -> Color(0xFFF44336).copy(alpha = pulseAlpha * 0.85f)
        isUrgent    -> Color(0xFFFF8C00).copy(alpha = 0.75f)
        else        -> Gold.copy(alpha = 0.5f)
    }

    val borderColor = when {
        isSelected       -> Gold
        isCritical && !alreadyPredicted && !isExpired -> Color(0xFFF44336).copy(alpha = 0.6f)
        alreadyPredicted -> Color(0xFF1E3A1E)
        isExpired        -> Color(0xFF1E1E1E)
        else             -> Color(0xFF242424)
    }
    val cardBg = when {
        isSelected       -> Color(0xFF1A1500)
        alreadyPredicted -> Color(0xFF0D1A0D)
        isExpired        -> Color(0xFF0A0A0A)
        else             -> Color(0xFF111111)
    }

    // Match date label — parsed once per fixture, shown in footer when not urgent
    val matchDateLabel = remember(fixture.date) {
        if (fixture.date.isBlank()) ""
        else try {
            val stripped = fixture.date
                .substringBefore("+").substringBefore("Z").let { s ->
                    if (s.length > 19) s.substring(0, 19) else s
                }
            val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            utcParser.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = utcParser.parse(stripped)
            val displayFmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
            parsed?.let { displayFmt.format(it) } ?: ""
        } catch (_: Exception) { "" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !alreadyPredicted && !isExpired, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column {
            // Team names row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val homeFlag = flagFor(fixture.homeTeam)
                    if (homeFlag.isNotEmpty()) Text(homeFlag, fontSize = 14.sp)
                    Text(
                        fixture.homeTeam,
                        color = if (isExpired) Dim else Color.White,
                        fontFamily = ironManFont,
                        fontSize = 15.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .background(
                            if (isSelected) Gold.copy(alpha = 0.12f) else Color(0xFF1A1A1A),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "VS",
                        color = if (isSelected) Gold else Color(0xFF555555),
                        fontFamily = ironManFont,
                        fontSize = 11.sp
                    )
                }
                // Away team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        fixture.awayTeam,
                        color = if (isExpired) Dim else Color.White,
                        fontFamily = ironManFont,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    val awayFlag = flagFor(fixture.awayTeam)
                    if (awayFlag.isNotEmpty()) Text(awayFlag, fontSize = 14.sp)
                }
            }

            // Footer row
            HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when {
                    alreadyPredicted -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF4CAF50), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Predicted", color = Color(0xFF4CAF50), fontSize = 12.sp, fontFamily = ironManFont)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCaptained) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF0D1A2E), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0xFF4B9EFF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("🎖️ CAPTAIN", color = Color(0xFF4B9EFF), fontFamily = ironManFont, fontSize = 10.sp)
                                }
                            }
                            if (existingPrediction != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${existingPrediction.homeGoals} – ${existingPrediction.awayGoals}",
                                    color = Color(0xFF4CAF50),
                                    fontFamily = ironManFont,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    isExpired -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF444444), androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Closed", color = Dim, fontSize = 12.sp)
                        }
                    }
                    deadline != null -> {
                        val hours = totalDeadlineMins / 60
                        val countdownText = when {
                            totalDeadlineMins == Long.MAX_VALUE -> ""
                            isCritical -> "${totalDeadlineMins}m"
                            isUrgent   -> "${hours}h ${totalDeadlineMins % 60}m"
                            else       -> { val d = hours / 24; if (d > 0) "${d}d ${hours % 24}h" else "${hours}h" }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .background(urgencyDotColor, androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (matchDateLabel.isNotEmpty()) "Kicks off $matchDateLabel" else ctaLabel,
                                color = urgencyTextColor.copy(alpha = pulseAlpha),
                                fontSize = 12.sp
                            )
                        }
                        if (countdownText.isNotEmpty()) {
                            Text(
                                countdownText,
                                color = ctaColor,
                                fontFamily = ironManFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Captain CTA
            if (canCaptain) {
                HorizontalDivider(color = Color(0xFF1A1A1A), thickness = 1.dp)
                TextButton(onClick = onCaptainTap, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "🎖️  Set as Captain  ·  2× points",
                        color = Color(0xFF4B9EFF),
                        fontFamily = ironManFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreEntryCard(
    fixture: FootballFixture,
    homeGoals: Int,
    awayGoals: Int,
    onHomeGoalsChange: (Int) -> Unit,
    onAwayGoalsChange: (Int) -> Unit,
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

            // Shared 3-slot layout (weight | separator | weight) used for BOTH the
            // team-name row and the stepper row, so each name sits dead-centre over
            // its stepper and the "—" lines up with the score numbers.
            val sepWidth = 32.dp

            // Team names — bottom-aligned so a 2-line name still sits just above its stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                NameLabel(fixture.homeTeam, ironManFont, Modifier.weight(1f))
                Spacer(Modifier.width(sepWidth))
                NameLabel(fixture.awayTeam, ironManFont, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Steppers + centre separator, all vertically aligned on one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GoalStepper(
                        value = homeGoals,
                        onDecrement = { if (homeGoals > 0) onHomeGoalsChange(homeGoals - 1) },
                        onIncrement = { onHomeGoalsChange(homeGoals + 1) },
                        accentColor = Gold,
                        ironManFont = ironManFont
                    )
                }
                Box(modifier = Modifier.width(sepWidth), contentAlignment = Alignment.Center) {
                    Text("—", color = Color(0xFF444444), fontSize = 28.sp)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GoalStepper(
                        value = awayGoals,
                        onDecrement = { if (awayGoals > 0) onAwayGoalsChange(awayGoals - 1) },
                        onIncrement = { onAwayGoalsChange(awayGoals + 1) },
                        accentColor = Gold,
                        ironManFont = ironManFont
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold
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
    canWildcard: Boolean,
    isWildcardActive: Boolean,
    ironManFont: FontFamily,
    onWildcardTap: () -> Unit
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
            val normalisedWinner = fixture.winner.trim().lowercase()
            val normalisedHome = fixture.homeTeam.trim().lowercase()
            val normalisedAway = fixture.awayTeam.trim().lowercase()

            val actualResult = when {
                normalisedWinner == "draw" || normalisedWinner == "d" -> "draw"
                normalisedWinner == "home" || normalisedWinner == normalisedHome -> "home"
                normalisedWinner == "away" || normalisedWinner == normalisedAway -> "away"
                else -> normalisedWinner
            }

            if (predictedResult == actualResult) Color(0xFFFFA500) else Color(0xFFF44336)
        }
    }

    val wildcardBorder = if (isWildcardActive)
        BorderStroke(1.dp, WildcardColor.copy(alpha = 0.6f))
    else
        BorderStroke(1.dp, Color(0xFF2A2A2A))

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(10.dp),
        border = wildcardBorder
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Home team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val homeFlag = flagFor(fixture.homeTeam)
                    if (homeFlag.isNotEmpty()) Text(homeFlag, fontSize = 13.sp)
                    Text(
                        fixture.homeTeam, color = Color.White,
                        fontFamily = ironManFont, fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (prediction != null) "${prediction.homeGoals}  :  ${prediction.awayGoals}" else "—  :  —",
                        color = pointColor, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                    if (prediction?.wildcardUsed == true) {
                        Text("🃏 wildcarded", color = WildcardColor, fontSize = 10.sp)
                    }
                }
                // Away team + flag
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        fixture.awayTeam, color = Color.White,
                        fontFamily = ironManFont, fontSize = 13.sp,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.End
                    )
                    val awayFlag = flagFor(fixture.awayTeam)
                    if (awayFlag.isNotEmpty()) Text(awayFlag, fontSize = 13.sp)
                }
            }

            if (canWildcard) {
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
                TextButton(
                    onClick = onWildcardTap,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isWildcardActive) "✕  Cancel" else "🃏  Use Wildcard",
                        color = if (isWildcardActive) Dim else WildcardColor,
                        fontFamily = ironManFont,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WildcardEntryCard(
    fixture: FootballFixture,
    homeGoals: Int,
    awayGoals: Int,
    onHomeGoalsChange: (Int) -> Unit,
    onAwayGoalsChange: (Int) -> Unit,
    ironManFont: FontFamily,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF160D20)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WildcardColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🃏  WILDCARD", color = WildcardColor, fontFamily = ironManFont, fontSize = 13.sp, letterSpacing = 1.sp)
            Text("Change your prediction — one use per season", color = Dim, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    val homeFlag = flagFor(fixture.homeTeam)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (homeFlag.isNotEmpty()) {
                            Text(homeFlag, fontSize = 14.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(fixture.homeTeam, color = WildcardColor, fontFamily = ironManFont, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GoalStepper(
                        value = homeGoals,
                        onDecrement = { if (homeGoals > 0) onHomeGoalsChange(homeGoals - 1) },
                        onIncrement = { onHomeGoalsChange(homeGoals + 1) },
                        accentColor = WildcardColor,
                        ironManFont = ironManFont
                    )
                }
                Text("—", color = Color(0xFF444444), fontSize = 28.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    val awayFlag = flagFor(fixture.awayTeam)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (awayFlag.isNotEmpty()) {
                            Text(awayFlag, fontSize = 14.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(fixture.awayTeam, color = WildcardColor, fontFamily = ironManFont, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GoalStepper(
                        value = awayGoals,
                        onDecrement = { if (awayGoals > 0) onAwayGoalsChange(awayGoals - 1) },
                        onIncrement = { onAwayGoalsChange(awayGoals + 1) },
                        accentColor = WildcardColor,
                        ironManFont = ironManFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WildcardColor
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm Wildcard", color = Color.White, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── GW Deadline Banner ────────────────────────────────────────────────────────
// Shows the single gameweek deadline (= first kickoff) above all fixture cards.
// Replaces the confusing per-fixture countdown that used to appear on each card.

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun GwDeadlineBanner(
    gwDeadline: LocalDateTime,
    now: LocalDateTime,
    ironManFont: FontFamily
) {
    val d = Duration.between(now, gwDeadline)
    val isLocked = d.isNegative || d.isZero

    val totalMins  = if (isLocked) 0L else d.toMinutes()
    val totalHours = if (isLocked) 0L else d.toHours()
    val isCritical = !isLocked && totalMins < 10
    val isUrgent   = !isLocked && totalMins < 60

    val color = when {
        isLocked   -> Color(0xFF555555)
        isCritical -> Color(0xFFF44336)
        isUrgent   -> Color(0xFFFF8C00)
        else       -> Gold
    }

    val pulseTransition = rememberInfiniteTransition(label = "banner_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bannerAlpha"
    )
    val alpha = if (isCritical) pulseAlpha else 1f

    val countdownText = when {
        isLocked   -> "LOCKED"
        totalHours >= 24 -> {
            val days  = totalHours / 24
            val hours = totalHours % 24
            "${days}d ${hours}h"
        }
        totalHours > 0 -> {
            val mins = d.toMinutes() % 60
            "${totalHours}h ${mins}m"
        }
        else -> "${totalMins}m"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f * alpha), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (isLocked) "🔒" else "⏱️", fontSize = 13.sp)
            Text(
                if (isLocked) "PREDICTIONS LOCKED" else "GW CLOSES IN",
                color = color.copy(alpha = alpha),
                fontFamily = ironManFont,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
        Text(
            countdownText,
            color = color.copy(alpha = alpha),
            fontFamily = ironManFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
