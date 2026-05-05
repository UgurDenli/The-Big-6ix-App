@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.invenium.thebig6ix.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val ironManFont = remember { FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold)) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> OnboardingPage1(ironManFont)
                        1 -> OnboardingPage2(ironManFont)
                    }
                }
            }

            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .let {
                                if (pagerState.currentPage == index)
                                    it.then(Modifier.padding(0.dp))
                                else it
                            }
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (pagerState.currentPage == index) Color(0xFFFFD700) else Color.DarkGray,
                            shape = CircleShape
                        ) {}
                    }
                }
            }

            if (pagerState.currentPage == 1) {
                Button(
                    onClick = {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            firestore.collection("users").document(userId)
                                .update("completedOnboarding", true)
                        }
                        onFinished()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text("Enter App", color = Color.Black, fontFamily = ironManFont, fontSize = 20.sp)
                }
            } else {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Next →", color = Color(0xFFFFD700), fontFamily = ironManFont, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage1(ironManFont: FontFamily) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👋", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to The Big 6ix!",
            color = Color(0xFFFFD700),
            fontFamily = ironManFont,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Predict Premier League scorelines each gameweek and compete with the community to top the leaderboard.",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun OnboardingPage2(ironManFont: FontFamily) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚽", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "How Scoring Works",
            color = Color(0xFFFFD700),
            fontFamily = ironManFont,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        ScoringRow("🟢  3 pts", "Exact score match", ironManFont)
        Spacer(modifier = Modifier.height(12.dp))
        ScoringRow("🟠  1 pt", "Correct result (W/D/L)", ironManFont)
        Spacer(modifier = Modifier.height(12.dp))
        ScoringRow("🔴  0 pts", "Wrong result or no prediction", ironManFont)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Submitted predictions are final — just like the show!",
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScoringRow(points: String, description: String, ironManFont: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(points, color = Color.White, fontFamily = ironManFont, fontSize = 16.sp, modifier = Modifier.width(120.dp))
        Text(description, color = Color.LightGray, fontSize = 15.sp)
    }
}
