package com.invenium.thebig6ix.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invenium.thebig6ix.R

private const val DISCORD_OAUTH_URL =
    "https://discord.com/oauth2/authorize" +
    "?client_id=1402215849183674478" +
    "&redirect_uri=https%3A%2F%2Fthe-big-6ix.web.app%2Fdiscord-callback.html" +
    "&response_type=code" +
    "&scope=identify%20guilds%20guilds.members.read"

private val Gold       = Color(0xFFFFD700)
private val Discord    = Color(0xFF5865F2)
private val CardBg     = Color(0xFF111111)

@Composable
fun LoginScreen(onLoginSuccess: (needsOnboarding: Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val viewModel: DiscordAuthViewModel = viewModel(viewModelStoreOwner = activity)
    val loginState by viewModel.loginState.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is DiscordAuthViewModel.LoginState.Success -> {
                viewModel.clearState()
                onLoginSuccess(state.needsOnboarding)
            }
            is DiscordAuthViewModel.LoginState.Error -> {
                errorMessage = state.message
                viewModel.clearState()
            }
            else -> {}
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color(0xFFFFD700),
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Access Denied", fontFamily = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold))) },
            text = { Text(msg, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = Color(0xFFFFD700), fontFamily = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, FontWeight.Bold)))
                }
            }
        )
    }

    val isLoading = loginState is DiscordAuthViewModel.LoginState.Loading
    val ironManFont = FontFamily(Font(R.font.iron_man_of_war_001c_ncv, weight = FontWeight.Bold))

    // Subtle pulse animation on the glow when idle
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glowScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Subtle radial glow behind the logo
        Box(
            modifier = Modifier
                .size(480.dp)
                .scale(glowScale)
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x1AFFD700),
                            Color(0x0AFFD700),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo badge
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0x26FFD700), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_tbsix),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(130.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "THE BIG 6IX",
                fontFamily = ironManFont,
                fontSize = 46.sp,
                letterSpacing = 3.sp,
                color = Gold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "PREDICTIONS LEAGUE",
                fontFamily = ironManFont,
                fontSize = 14.sp,
                letterSpacing = 4.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Membership notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Sign in with your Discord account to access the Predictions League. You must be a member of The Big 6ix Discord server.",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Discord sign-in button
            Button(
                onClick = {
                    if (!isLoading) {
                        val customTabIntent = CustomTabsIntent.Builder().build()
                        customTabIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        customTabIntent.launchUrl(context, Uri.parse(DISCORD_OAUTH_URL))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Discord),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Signing in…",
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontSize = 16.sp
                    )
                } else {
                    // Discord "D" wordmark as styled text
                    Text(
                        "⊕",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        "Sign in with Discord",
                        color = Color.White,
                        fontFamily = ironManFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Privacy Policy",
                modifier = Modifier
                    .clickable {
                        CustomTabsIntent.Builder().build()
                            .launchUrl(context, Uri.parse("https://thebig6ix.co.uk/the-big-6ix-privacy-policy/"))
                    }
                    .padding(8.dp),
                color = Color(0xFF555555),
                fontFamily = ironManFont,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
