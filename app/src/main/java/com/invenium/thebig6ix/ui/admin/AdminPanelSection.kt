package com.invenium.thebig6ix.ui.admin

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val Gold   = Color(0xFFFFD700)
private val CardBg = Color(0xFF111111)
private val Dim    = Color(0xFF888888)
private val Red    = Color(0xFFE53935)

// ─────────────────────────────────────────────────────────────────────────────
// AdminPanelSection
//
// Drop-in replacement for the simple admin Card in PredictionScreen.
// It takes the existing GW-override callbacks from PredictionViewModel plus
// its own AdminViewModel for fixture editing, creation, scoring and resets.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdminPanelSection(
    currentGameweek: Int?,
    adminGameweekInput: String,
    onAdminGameweekInputChange: (String) -> Unit,
    onSetOverride: () -> Unit,
    onSnapToNext: () -> Unit,
    onClearOverride: () -> Unit,
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    // Observe ViewModel state
    val fixtures     by adminViewModel.fixturesForGw.collectAsState()
    val isLoading    by adminViewModel.isLoading.collectAsState()
    val isBusy       by adminViewModel.isBusy.collectAsState()
    val toastMessage by adminViewModel.toast.collectAsState()
    val auditEntries by adminViewModel.auditEntries.collectAsState()
    val isAuditLoading by adminViewModel.isAuditLoading.collectAsState()

    // Show toast when message arrives
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            adminViewModel.clearToast()
        }
    }

    // ── Section expand state ──────────────────────────────────────────────────
    var fixtureEditorExpanded  by remember { mutableStateOf(false) }
    var createFixtureExpanded  by remember { mutableStateOf(false) }
    var auditExpanded          by remember { mutableStateOf(false) }
    var auditGw                by remember { mutableStateOf(currentGameweek?.toString() ?: "") }

    // ── Fixture editor state ──────────────────────────────────────────────────
    var editorGw           by remember { mutableStateOf(currentGameweek?.toString() ?: "") }
    var editingId          by remember { mutableStateOf<String?>(null) }
    var editHome           by remember { mutableStateOf("") }
    var editAway           by remember { mutableStateOf("") }

    // Keep editorGw in sync when currentGameweek changes (first open)
    LaunchedEffect(currentGameweek) {
        if (editorGw.isEmpty() && currentGameweek != null) {
            editorGw = currentGameweek.toString()
        }
    }

    // ── Create fixture state ──────────────────────────────────────────────────
    var createHome     by remember { mutableStateOf("") }
    var createAway     by remember { mutableStateOf("") }
    var createGw       by remember { mutableStateOf("") }
    var createDate     by remember { mutableStateOf("") }   // "YYYY-MM-DD"
    var createTime     by remember { mutableStateOf("") }   // "HH:mm"
    var createDateErr  by remember { mutableStateOf(false) }

    // ── Single-user token reset state ────────────────────────────────────────
    var userMgmtExpanded      by remember { mutableStateOf(false) }
    var singleUserName        by remember { mutableStateOf("") }
    var showSingleUserConfirm by remember { mutableStateOf(false) }

    if (showSingleUserConfirm) {
        AlertDialog(
            onDismissRequest = { showSingleUserConfirm = false },
            containerColor   = Color(0xFF1A1A1A),
            titleContentColor = Color(0xFF4CAF50),
            textContentColor  = Color(0xFFCCCCCC),
            title = { Text("Reset User Tokens?", fontFamily = ironManFont) },
            text  = {
                Text(
                    "Reset wildcard, captain & double-down for \"${singleUserName.trim()}\".",
                    fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSingleUserConfirm = false
                    adminViewModel.resetUserTokensByName(singleUserName.trim())
                    singleUserName = ""
                }) {
                    Text("RESET", color = Color(0xFF4CAF50), fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSingleUserConfirm = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Token reset confirm dialog ────────────────────────────────────────────
    var showTokenResetConfirm by remember { mutableStateOf(false) }

    if (showTokenResetConfirm) {
        AlertDialog(
            onDismissRequest = { showTokenResetConfirm = false },
            containerColor   = Color(0xFF1A1A1A),
            titleContentColor = Gold,
            textContentColor  = Color(0xFFCCCCCC),
            title = { Text("Reset Tokens?", fontFamily = ironManFont) },
            text  = {
                Text(
                    "This will set wildcardAvailable, captainAvailable and " +
                    "doubleDownAvailable back to true for every user in the app.",
                    fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTokenResetConfirm = false
                    adminViewModel.resetAllTokens()
                }) {
                    Text("RESET TOKENS", color = Gold, fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenResetConfirm = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Season reset confirm dialog ───────────────────────────────────────────
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor   = Color(0xFF1A1A1A),
            titleContentColor = Red,
            textContentColor  = Color(0xFFCCCCCC),
            title = { Text("Reset Season?", fontFamily = ironManFont) },
            text  = {
                Text(
                    "This will archive all user scores to the seasons collection " +
                    "and reset everyone's score, weeklyScore and monthlyScore to 0. " +
                    "It will also restore all tokens (wildcard, captain, doubledown). " +
                    "This cannot be undone.",
                    fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    adminViewModel.triggerSeasonReset()
                }) {
                    Text("RESET", color = Red, fontFamily = ironManFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = Dim, fontFamily = ironManFont)
                }
            }
        )
    }

    // ── Root Card ─────────────────────────────────────────────────────────────
    Card(
        colors  = CardDefaults.cardColors(containerColor = CardBg),
        shape   = RoundedCornerShape(12.dp),
        border  = BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header
            Text(
                "Admin Panel",
                color      = Gold,
                fontFamily = ironManFont,
                fontSize   = 15.sp,
                letterSpacing = 1.sp
            )

            // ── 1. Gameweek Override ──────────────────────────────────────────
            SectionDivider()
            Text("GAMEWEEK OVERRIDE", color = Dim, fontFamily = ironManFont, fontSize = 11.sp)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminTextField(
                    value    = adminGameweekInput,
                    onChange = onAdminGameweekInputChange,
                    label    = "GW",
                    modifier = Modifier.width(80.dp),
                    keyboardType = KeyboardType.Number
                )
                AdminChip("Set",       Gold,   Color.Black,  ironManFont) { onSetOverride() }
                AdminChip("Snap",      Gold,   Color.Black,  ironManFont) { onSnapToNext() }
                AdminChip("Clear",     Dim,    Color.Black,  ironManFont) { onClearOverride() }
            }

            // ── 2. Fixture Editor ─────────────────────────────────────────────
            SectionDivider()
            SectionHeader(
                title      = "FIXTURE EDITOR",
                expanded   = fixtureEditorExpanded,
                ironManFont = ironManFont
            ) { fixtureEditorExpanded = !fixtureEditorExpanded }

            AnimatedVisibility(visible = fixtureEditorExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // GW selector + Load button
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminTextField(
                            value    = editorGw,
                            onChange = { editorGw = it; editingId = null },
                            label    = "GW",
                            modifier = Modifier.width(80.dp),
                            keyboardType = KeyboardType.Number
                        )
                        AdminChip(
                            label      = if (isLoading) "…" else "Load",
                            bg         = Gold,
                            fg         = Color.Black,
                            font       = ironManFont,
                            enabled    = !isLoading
                        ) {
                            editorGw.toIntOrNull()?.let { adminViewModel.loadFixturesByGameweek(it) }
                        }
                    }

                    if (isLoading) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp))
                    }

                    // Fixture list
                    fixtures.forEach { fixture ->
                        val isEditing = editingId == fixture.id
                        val scoreText = when {
                            fixture.homeTeamGoals >= 0 -> "${fixture.homeTeamGoals} – ${fixture.awayTeamGoals}"
                            else                       -> "TBD"
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                            shape  = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${fixture.homeTeam} vs ${fixture.awayTeam}",
                                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        Text(scoreText, color = Dim, fontSize = 11.sp, fontFamily = ironManFont)
                                    }
                                    TextButton(
                                        onClick = {
                                            if (isEditing) {
                                                editingId = null
                                            } else {
                                                editingId = fixture.id
                                                editHome = if (fixture.homeTeamGoals >= 0) fixture.homeTeamGoals.toString() else ""
                                                editAway = if (fixture.awayTeamGoals >= 0) fixture.awayTeamGoals.toString() else ""
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (isEditing) "Cancel" else "Edit",
                                            color = if (isEditing) Dim else Gold,
                                            fontFamily = ironManFont, fontSize = 12.sp
                                        )
                                    }
                                }

                                if (isEditing) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AdminTextField(
                                            value    = editHome,
                                            onChange = { editHome = it },
                                            label    = "Home",
                                            modifier = Modifier.width(70.dp),
                                            keyboardType = KeyboardType.Number
                                        )
                                        Text("–", color = Dim, fontSize = 16.sp)
                                        AdminTextField(
                                            value    = editAway,
                                            onChange = { editAway = it },
                                            label    = "Away",
                                            modifier = Modifier.width(70.dp),
                                            keyboardType = KeyboardType.Number
                                        )
                                        AdminChip(
                                            label   = "Save",
                                            bg      = Gold,
                                            fg      = Color.Black,
                                            font    = ironManFont,
                                            enabled = editHome.toIntOrNull() != null && editAway.toIntOrNull() != null
                                        ) {
                                            adminViewModel.updateFixtureScore(
                                                fixtureId  = fixture.id,
                                                homeGoals  = editHome.toInt(),
                                                awayGoals  = editAway.toInt()
                                            )
                                            editingId = null
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isLoading && fixtures.isEmpty() && editorGw.isNotEmpty()) {
                        Text("No fixtures loaded. Tap Load.", color = Dim, fontSize = 12.sp)
                    }
                }
            }

            // ── 3. Create Fixture ─────────────────────────────────────────────
            SectionDivider()
            SectionHeader(
                title       = "CREATE FIXTURE",
                expanded    = createFixtureExpanded,
                ironManFont = ironManFont
            ) { createFixtureExpanded = !createFixtureExpanded }

            AnimatedVisibility(visible = createFixtureExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTextField(
                        value    = createHome,
                        onChange = { createHome = it },
                        label    = "Home Team",
                        modifier = Modifier.fillMaxWidth()
                    )
                    AdminTextField(
                        value    = createAway,
                        onChange = { createAway = it },
                        label    = "Away Team",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminTextField(
                            value    = createGw,
                            onChange = { createGw = it },
                            label    = "GW",
                            modifier = Modifier.width(80.dp),
                            keyboardType = KeyboardType.Number
                        )
                        AdminTextField(
                            value    = createDate,
                            onChange = { createDate = it; createDateErr = false },
                            label    = "Date (YYYY-MM-DD)",
                            modifier = Modifier.weight(1f),
                            isError  = createDateErr
                        )
                    }
                    AdminTextField(
                        value    = createTime,
                        onChange = { createTime = it; createDateErr = false },
                        label    = "Time (HH:mm, UTC+1)",
                        modifier = Modifier.width(160.dp),
                        isError  = createDateErr
                    )
                    if (createDateErr) {
                        Text("Invalid date/time. Use YYYY-MM-DD and HH:mm.", color = Red, fontSize = 11.sp)
                    }

                    val canCreate = createHome.isNotBlank() && createAway.isNotBlank() &&
                                   createGw.toIntOrNull() != null &&
                                   createDate.isNotBlank() && createTime.isNotBlank()

                    AdminChip(
                        label   = "Create Fixture",
                        bg      = Gold,
                        fg      = Color.Black,
                        font    = ironManFont,
                        enabled = canCreate
                    ) {
                        val ms = parseDeadlineMs("$createDate $createTime")
                        if (ms == null) {
                            createDateErr = true
                        } else {
                            adminViewModel.createFixture(
                                homeTeam    = createHome,
                                awayTeam    = createAway,
                                gameweek    = createGw.toInt(),
                                deadlineMs  = ms
                            )
                            createHome = ""; createAway = ""; createGw = ""; createDate = ""; createTime = ""
                        }
                    }
                }
            }

            // ── 3b. User Management ───────────────────────────────────────────
            SectionDivider()
            SectionHeader(
                title       = "USER MANAGEMENT",
                expanded    = userMgmtExpanded,
                ironManFont = ironManFont
            ) { userMgmtExpanded = !userMgmtExpanded }

            AnimatedVisibility(visible = userMgmtExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminTextField(
                        value    = singleUserName,
                        onChange = { singleUserName = it },
                        label    = "Player Name",
                        modifier = Modifier.fillMaxWidth()
                    )
                    AdminChip(
                        label   = "Reset Tokens for Player",
                        bg      = Color(0xFF1A2A1A),
                        fg      = Color(0xFF4CAF50),
                        font    = ironManFont,
                        enabled = singleUserName.isNotBlank() && !isBusy
                    ) { showSingleUserConfirm = true }
                }
            }

            // ── 4. Controls ───────────────────────────────────────────────────
            SectionDivider()
            Text("CONTROLS", color = Dim, fontFamily = ironManFont, fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { adminViewModel.triggerScoring() },
                    enabled  = !isBusy,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        disabledContainerColor = Color(0xFF555500)
                    ),
                    shape    = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isBusy) "Working…" else "Trigger Scoring",
                        color = Color.Black, fontFamily = ironManFont, fontSize = 12.sp
                    )
                }
            }

            // GW winner notification
            var notifyGwInput by remember { mutableStateOf(currentGameweek?.toString() ?: "") }
            LaunchedEffect(currentGameweek) {
                if (notifyGwInput.isEmpty() && currentGameweek != null) notifyGwInput = currentGameweek.toString()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminTextField(
                    value    = notifyGwInput,
                    onChange = { notifyGwInput = it },
                    label    = "GW",
                    modifier = Modifier.width(80.dp),
                    keyboardType = KeyboardType.Number
                )
                Button(
                    onClick  = { notifyGwInput.toIntOrNull()?.let { adminViewModel.sendGwWinnerNotification(it) } },
                    enabled  = !isBusy && notifyGwInput.toIntOrNull() != null,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A2A),
                        disabledContainerColor = Color(0xFF111122)
                    ),
                    border   = BorderStroke(1.dp, Color(0xFF4B9EFF)),
                    shape    = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("🔔  Send GW Notification", color = Color(0xFF4B9EFF), fontFamily = ironManFont, fontSize = 12.sp)
                }
            }

            Button(
                onClick  = { showTokenResetConfirm = true },
                enabled  = !isBusy,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A2A1A),
                    disabledContainerColor = Color(0xFF111A11)
                ),
                border   = BorderStroke(1.dp, Color(0xFF4CAF50)),
                shape    = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔑  Reset All Tokens", color = Color(0xFF4CAF50), fontFamily = ironManFont, fontSize = 12.sp)
            }

            Button(
                onClick  = { showResetConfirm = true },
                enabled  = !isBusy,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A0000),
                    disabledContainerColor = Color(0xFF2A0000)
                ),
                border   = BorderStroke(1.dp, Red),
                shape    = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚨  Reset Season", color = Red, fontFamily = ironManFont, fontSize = 12.sp)
            }

            // ── 4. Points Audit ──────────────────────────────────────────────
            SectionDivider()
            SectionHeader(
                title = "POINTS AUDIT",
                expanded = auditExpanded,
                ironManFont = ironManFont
            ) { auditExpanded = !auditExpanded }

            AnimatedVisibility(visible = auditExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminTextField(
                            value = auditGw,
                            onChange = { auditGw = it },
                            label = "GW",
                            modifier = Modifier.width(80.dp),
                            keyboardType = KeyboardType.Number
                        )
                        AdminChip(
                            label = if (isAuditLoading) "…" else "Load",
                            bg = Gold,
                            fg = Color.Black,
                            font = ironManFont,
                            enabled = !isAuditLoading
                        ) {
                            auditGw.toIntOrNull()?.let { adminViewModel.loadPointsAudit(it) }
                        }
                    }

                    if (isAuditLoading) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp))
                    }

                    if (auditEntries.isNotEmpty()) {
                        Text(
                            "${auditEntries.size} predictions | ${auditEntries.sumOf { it.points }} total pts",
                            color = Dim,
                            fontSize = 11.sp
                        )
                        auditEntries.forEach { entry ->
                            val pointColor = when {
                                entry.points >= 3 -> Color(0xFF4CAF50)
                                entry.points >= 1 -> Color(0xFFFFA500)
                                else -> Color(0xFFF44336)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.userName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${entry.homeTeam} vs ${entry.awayTeam}",
                                            color = Dim,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            "Pick: ${entry.predictedHome}–${entry.predictedAway}  |  Actual: ${entry.actualHome}–${entry.actualAway}",
                                            color = Dim,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(pointColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            "${entry.points} pts",
                                            color = pointColor,
                                            fontFamily = ironManFont,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isAuditLoading && auditEntries.isEmpty() && auditExpanded) {
                        Text("No scored predictions for this GW", color = Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helpers
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onChange,
        label          = { Text(label, fontSize = 11.sp) },
        isError        = isError,
        singleLine     = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor      = Color.White,
            unfocusedTextColor    = Color.White,
            focusedBorderColor    = Color(0xFFFFD700),
            unfocusedBorderColor  = Color(0xFF444444),
            focusedLabelColor     = Color(0xFF888888),
            unfocusedLabelColor   = Color(0xFF888888),
            errorBorderColor      = Color(0xFFE53935)
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        modifier  = modifier.height(56.dp)
    )
}

@Composable
private fun AdminChip(
    label: String,
    bg: Color,
    fg: Color,
    font: FontFamily,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        enabled  = enabled,
        colors   = ButtonDefaults.buttonColors(
            containerColor         = bg,
            disabledContainerColor = Color(0xFF333333)
        ),
        shape    = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (enabled) fg else Color(0xFF666666), fontFamily = font, fontSize = 12.sp)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    ironManFont: FontFamily,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            title,
            color      = Color(0xFF888888),
            fontFamily = ironManFont,
            fontSize   = 11.sp,
            modifier   = Modifier.weight(1f)
        )
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(
                if (expanded) "▲" else "▼",
                color = Color(0xFFFFD700), fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Date/time parsing
// ─────────────────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
private fun parseDeadlineMs(dateTimeStr: String): Long? {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val ldt = LocalDateTime.parse(dateTimeStr.trim(), formatter)
        ldt.atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    } catch (_: Exception) {
        null
    }
}
