package com.invenium.thebig6ix.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.invenium.thebig6ix.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Gold   = Color(0xFFFFD700)
private val CardBg = Color(0xFF111111)
private val Dim    = Color(0xFF888888)

@Composable
fun ProfileScreen(navController: NavController) {
    val auth      = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val storage   = FirebaseStorage.getInstance()
    val user      = auth.currentUser ?: return
    val scope     = rememberCoroutineScope()
    val context   = LocalContext.current
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    val profileStatsViewModel: ProfileViewModel = viewModel()
    val stats by profileStatsViewModel.stats.collectAsState()

    var profileImageUrl by remember { mutableStateOf("") }
    var displayName     by remember { mutableStateOf("User") }
    var points          by remember { mutableStateOf(0) }
    var isLoadingImage  by remember { mutableStateOf(true) }

    // ── Badge unlock tracking ────────────────────────────────────────────────
    val prefs = remember { context.getSharedPreferences("badge_prefs", Context.MODE_PRIVATE) }
    var newlyUnlockedBadge by remember { mutableStateOf<Badge?>(null) }

    LaunchedEffect(stats.badges) {
        if (stats.isLoading || stats.badges.isEmpty()) return@LaunchedEffect
        val seenIds   = prefs.getStringSet("earned_badges", emptySet()) ?: emptySet()
        val earned    = stats.badges.filter { it.earned }
        val newBadges = earned.filter { it.id !in seenIds }
        if (newBadges.isNotEmpty()) {
            newlyUnlockedBadge = newBadges.first()
            prefs.edit()
                .putStringSet("earned_badges", (seenIds + earned.map { it.id }).toSet())
                .apply()
        }
    }

    newlyUnlockedBadge?.let { badge ->
        BadgeUnlockDialog(badge = badge, ironManFont = ironManFont) {
            newlyUnlockedBadge = null
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val ref = storage.reference.child("profile_images/${user.uid}")
                    ref.putFile(selectedUri).await()
                    val url = ref.downloadUrl.await().toString()
                    firestore.collection("users").document(user.uid)
                        .update("profileImageUrl", url)
                        .addOnSuccessListener { profileImageUrl = url }
                } catch (e: Exception) {
                    Log.e("Profile", "Image upload failed", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val doc = firestore.collection("users").document(user.uid).get().await()
            profileImageUrl = doc.getString("profileImageUrl") ?: ""
            displayName     = doc.getString("fullName") ?: user.displayName ?: "User"
            points          = doc.getLong("score")?.toInt() ?: 0
            isLoadingImage  = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Gradient header ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D0D0D), Color.Black)))
                    .padding(top = 32.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingImage) {
                            CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        } else if (profileImageUrl.isNotBlank()) {
                            Image(
                                painter = rememberAsyncImagePainter(profileImageUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(painterResource(id = R.drawable.ic_account), null, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Box(
                        modifier = Modifier
                            .offset(y = (-12).dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("Tap to change", color = Dim, fontSize = 10.sp) }
                    Text(displayName, color = Color.White, fontFamily = ironManFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Score card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1400)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total Points", color = Dim, fontSize = 12.sp)
                        Text("$points", color = Gold, fontFamily = ironManFont, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                    }
                    Text("🏆", fontSize = 40.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Stats mini-cards ─────────────────────────────────────────────
            if (!stats.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniCard("ACCURACY",  "${stats.accuracyPercent}%",       "correct scores",   Gold,                  Modifier.weight(1f))
                    StatMiniCard("STREAK",    "${stats.currentStreak}",           if (stats.currentStreak == 1) "gameweek" else "gameweeks", Color(0xFF4CAF50), Modifier.weight(1f))
                    StatMiniCard("RESULTS",   "${stats.resultAccuracyPercent}%",  "correct results",  Color(0xFF4B9EFF),      Modifier.weight(1f))
                }

                Spacer(Modifier.height(20.dp))

                // ── GW points breakdown ──────────────────────────────────────
                if (stats.gwPointsList.isNotEmpty()) {
                    GwBreakdownSection(stats.gwPointsList, ironManFont)
                    Spacer(Modifier.height(16.dp))
                }

                // ── Achievements / badges ────────────────────────────────────
                if (stats.badges.isNotEmpty()) {
                    BadgesSection(stats.badges, ironManFont)
                    Spacer(Modifier.height(20.dp))
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }

            // ── Share stats button ────────────────────────────────────────────
            if (!stats.isLoading) {
                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            appendLine("🏆 My The Big 6ix Stats")
                            appendLine("━━━━━━━━━━━━━━━━━━━━")
                            appendLine("Total Points: $points")
                            appendLine("Score Accuracy: ${stats.accuracyPercent}%")
                            appendLine("Result Accuracy: ${stats.resultAccuracyPercent}%")
                            appendLine("Current Streak: ${stats.currentStreak} gameweek${if (stats.currentStreak != 1) "s" else ""}")
                            appendLine("━━━━━━━━━━━━━━━━━━━━")
                            append("Download The Big 6ix App 📱")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            putExtra(Intent.EXTRA_SUBJECT, "My Big 6ix Stats")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Stats"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(46.dp),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share My Stats", color = Gold, fontFamily = ironManFont, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── HEAD TO HEAD ─────────────────────────────────────────────────
            ProfileSection(title = "Head to Head") {
                ProfileRow(
                    label = "1v1 — Pick a player to compare",
                    icon  = Icons.Filled.EmojiEvents,
                    tint  = Gold
                ) {
                    navController.navigate("head_to_head_picker")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── MY STATS section ─────────────────────────────────────────────
            ProfileSection(title = "My Stats") {
                ProfileRow("Past Predictions", icon = Icons.Filled.History) {
                    navController.navigate("past_predictions")
                }
                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)
                ProfileRow("Season Archive", icon = Icons.Filled.Archive) {
                    navController.navigate("season_archive")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── COMMUNITY section ────────────────────────────────────────────
            ProfileSection(title = "Community") {
                ProfileRow(
                    label   = "YouTube Channel",
                    iconRes = R.drawable.ic_notifications_black_24dp,
                    tint    = Color(0xFFFF0000)
                ) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@TheBig6ix"))
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                }

                HorizontalDivider(color = Color(0xFF1E1E1E), thickness = 1.dp)

                ProfileRow(
                    label   = "Discord Server",
                    iconRes = R.drawable.ic_home_black_24dp,
                    tint    = Color(0xFF5865F2)
                ) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/invite/gf3FqaJH5M"))
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── ACCOUNT section ──────────────────────────────────────────────
            ProfileSection(title = "Account") {
                ProfileRow("Settings", icon = Icons.Filled.Settings) {
                    navController.navigate("profile_settings")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── GW points breakdown ───────────────────────────────────────────────────────

@Composable
private fun GwBreakdownSection(gwPointsList: List<Pair<Int, Int>>, ironManFont: FontFamily) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "GW BREAKDOWN",
            color = Dim, fontSize = 11.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val maxPts = gwPointsList.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
            gwPointsList.forEach { (gw, pts) ->
                val barFill  = pts.toFloat() / maxPts
                val ptColor  = when {
                    pts == 0    -> Dim
                    pts <= 2    -> Color(0xFFFFA500)
                    pts <= 5    -> Gold
                    else        -> Color(0xFF4CAF50)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(44.dp)
                ) {
                    // Points value
                    Text(
                        "$pts",
                        color = ptColor,
                        fontFamily = ironManFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    // Mini bar
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(40.dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(barFill.coerceAtLeast(0.05f))
                                .background(ptColor.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // GW label
                    Text(
                        "GW$gw",
                        color = Dim,
                        fontSize = 8.sp,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }
}

// ── Badges section ────────────────────────────────────────────────────────────

@Composable
private fun BadgesSection(badges: List<Badge>, ironManFont: FontFamily) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "ACHIEVEMENTS",
            color = Dim, fontSize = 11.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        val rows = (badges.size + 1) / 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(rows) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val i1 = rowIndex * 2
                    val i2 = i1 + 1
                    BadgeChip(badges[i1], ironManFont, Modifier.weight(1f))
                    if (i2 < badges.size) {
                        BadgeChip(badges[i2], ironManFont, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(badge: Badge, ironManFont: FontFamily, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (badge.earned) Color(0xFF1A1A1A) else Color(0xFF0D0D0D)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (badge.earned) Gold.copy(alpha = 0.45f) else Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                badge.emoji,
                fontSize = 20.sp,
                modifier = Modifier.graphicsLayer { alpha = if (badge.earned) 1f else 0.25f }
            )
            Column {
                Text(
                    badge.label,
                    color = if (badge.earned) Color.White else Dim,
                    fontFamily = ironManFont,
                    fontSize = 11.sp
                )
                Text(
                    badge.description,
                    color = Dim.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

// ── Badge unlock celebration dialog ──────────────────────────────────────────

@Composable
private fun BadgeUnlockDialog(badge: Badge, ironManFont: FontFamily, onDismiss: () -> Unit) {
    val scale by rememberInfiniteTransition(label = "badge_pulse").animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "badge_scale"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFF1A1200),
        shape            = RoundedCornerShape(20.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    badge.emoji,
                    fontSize = 52.sp,
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "BADGE UNLOCKED!",
                    color = Gold, fontFamily = ironManFont, fontSize = 16.sp, letterSpacing = 2.sp
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(Gold.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(badge.label, color = Color.White, fontFamily = ironManFont, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(badge.description, color = Dim, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("AWESOME! 🎉", color = Gold, fontFamily = ironManFont, fontSize = 13.sp)
            }
        }
    )
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun StatMiniCard(label: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color(0xFF888888), fontSize = 8.sp, letterSpacing = 1.sp, fontFamily = ironManFont)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontFamily = ironManFont, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color(0xFF555555), fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = title.uppercase(),
            color = Dim, fontSize = 11.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) { Column { content() } }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    tint: Color = Gold,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon    != null -> Icon(icon,                                  null, tint = tint, modifier = Modifier.size(18.dp))
                iconRes != null -> Icon(painterResource(id = iconRes), null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = Dim, modifier = Modifier.size(20.dp))
    }
}
