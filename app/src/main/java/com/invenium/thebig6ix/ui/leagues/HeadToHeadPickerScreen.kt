package com.invenium.thebig6ix.ui.leagues

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.ui.home.ShimmerBox
import kotlinx.coroutines.tasks.await

private val Gold   = Color(0xFFFFD700)
private val Dim    = Color(0xFF888888)
private val CardBg = Color(0xFF111111)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

data class PickerUser(
    val uid: String,
    val name: String,
    val score: Int,
    val profileImageUrl: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadToHeadPickerScreen(
    viewModel: MiniLeagueViewModel,
    onBack: () -> Unit = {},
    onPickedOpponent: () -> Unit = {}
) {
    val db          = FirebaseFirestore.getInstance()
    val currentUid  = FirebaseAuth.getInstance().currentUser?.uid
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var allUsers   by remember { mutableStateOf<List<PickerUser>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val snap = db.collection("users")
                .orderBy("score", Query.Direction.DESCENDING)
                .get().await()
            allUsers = snap.documents.mapNotNull { doc ->
                if (doc.id == currentUid) return@mapNotNull null   // exclude yourself
                val name = doc.getString("fullName") ?: return@mapNotNull null
                PickerUser(
                    uid             = doc.id,
                    name            = name,
                    score           = doc.getLong("score")?.toInt() ?: 0,
                    profileImageUrl = doc.getString("profileImageUrl")
                )
            }
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    val filtered = remember(allUsers, searchQuery) {
        if (searchQuery.isBlank()) allUsers
        else allUsers.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "HEAD TO HEAD",
                    color = Gold, fontFamily = ironManFont, fontSize = 20.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            // Subtitle
            Text(
                "Pick a player to go head to head against",
                color = Dim, fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )

            // Search bar
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search player name…", color = Dim) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = Dim) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    focusedBorderColor   = Gold,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor          = Gold,
                    focusedContainerColor   = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(8) {
                            ShimmerBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(62.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                filtered.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isBlank()) "No players found" else "No players matching \"$searchQuery\"",
                                color = Dim, fontFamily = ironManFont, fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(filtered) { index, player ->
                            val globalRank = allUsers.indexOf(player) + 1
                            val medalColor = when (globalRank) {
                                1    -> Gold
                                2    -> Silver
                                3    -> Bronze
                                else -> Color(0xFF2A2A2A)
                            }
                            val rankLabel = when (globalRank) {
                                1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"
                                else -> "#$globalRank"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectOpponent(player.uid, player.name)
                                        onPickedOpponent()
                                    },
                                colors = CardDefaults.cardColors(containerColor = CardBg),
                                shape  = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    if (globalRank <= 3) 1.5.dp else 1.dp,
                                    medalColor.copy(alpha = if (globalRank <= 3) 0.5f else 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        rankLabel,
                                        color = medalColor, fontFamily = ironManFont,
                                        fontSize = if (globalRank <= 3) 18.sp else 13.sp,
                                        modifier = Modifier.width(44.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, medalColor, CircleShape)
                                            .background(Color(0xFF222222)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (player.profileImageUrl != null) {
                                            Image(
                                                rememberAsyncImagePainter(player.profileImageUrl),
                                                null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Image(
                                                painterResource(id = R.drawable.ic_account),
                                                null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        player.name,
                                        color = Color.White, fontFamily = ironManFont,
                                        fontSize = 15.sp, modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${player.score} pts",
                                        color = Gold, fontFamily = ironManFont,
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
