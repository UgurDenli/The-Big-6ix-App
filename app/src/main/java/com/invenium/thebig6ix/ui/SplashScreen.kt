package com.invenium.thebig6ix.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invenium.thebig6ix.R
import kotlinx.coroutines.delay

private val SplashGold = Color(0xFFFFD700)

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))

    var entered  by remember { mutableStateOf(false) }
    var shimmer  by remember { mutableStateOf(false) }
    var subtitled by remember { mutableStateOf(false) }
    var exiting  by remember { mutableStateOf(false) }

    // ── Logo spring entrance ────────────────────────────────────────────────
    val logoScale by animateFloatAsState(
        targetValue  = if (entered) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue  = if (entered && !exiting) 1f else 0f,
        animationSpec = tween(450),
        label = "logoAlpha"
    )

    // ── Shimmer sweep (−0.4 → 1.4 in 900ms) ───────────────────────────────
    val shimmerX by animateFloatAsState(
        targetValue  = if (shimmer) 1.4f else -0.4f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "shimmerX"
    )

    // ── Decorative line ────────────────────────────────────────────────────
    val lineProgress by animateFloatAsState(
        targetValue  = if (subtitled && !exiting) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lineProgress"
    )

    // ── Subtitle fade ──────────────────────────────────────────────────────
    val subtitleAlpha by animateFloatAsState(
        targetValue  = if (subtitled && !exiting) 1f else 0f,
        animationSpec = tween(600),
        label = "subtitleAlpha"
    )

    // ── Screen exit fade ───────────────────────────────────────────────────
    val screenAlpha by animateFloatAsState(
        targetValue  = if (exiting) 0f else 1f,
        animationSpec = tween(420),
        label = "screenAlpha"
    )

    // ── Orchestration ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        entered   = true          // 0 ms  → logo bounces in
        delay(350)
        shimmer   = true          // 350ms → shimmer sweeps
        delay(400)
        subtitled = true          // 750ms → line + subtitle appear
        delay(1050)               // total ~1.8s visible
        exiting   = true          // fade out
        delay(450)                // wait for fade
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = screenAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo + shimmer ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(270.dp)
                    .height(76.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha  = logoAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_tbsix),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                // Gold shimmer sweep on top of the logo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx    = size.width * shimmerX
                    val halfW = 100f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.18f),
                                SplashGold.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            startX = cx - halfW,
                            endX   = cx + halfW
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Expanding gold line ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(200.dp * lineProgress)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                SplashGold.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(Modifier.height(18.dp))

            // ── Subtitle ───────────────────────────────────────────────────
            Text(
                text       = "THE BIG 6IX PREDICTIONS LEAGUE",
                color      = SplashGold.copy(alpha = subtitleAlpha * 0.65f),
                fontFamily = ironManFont,
                fontSize   = 24.sp,
                letterSpacing = 3.5.sp
            )
        }

        // ── Subtle vignette corners ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        radius = 1200f
                    )
                )
        )
    }
}
