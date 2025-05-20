package com.invenium.thebig6ix.ui.login

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.invenium.thebig6ix.R.string.default_web))
            .requestServerAuthCode(context.getString(com.invenium.thebig6ix.R.string.default_web), true)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube.channel-memberships.creator"))
            .build()
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                val authCode = account.serverAuthCode

                if (!idToken.isNullOrEmpty() && !authCode.isNullOrEmpty()) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential).addOnSuccessListener {
                        coroutineScope.launch {
                            verifyYouTubeMembershipViaCloudFunction(
                                context = context,
                                authCode = authCode,
                                onSuccess = onLoginSuccess,
                                onFailure = {
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }
            } catch (e: ApiException) {
                Log.e("LoginScreen", "Google SignIn failed", e)
                Toast.makeText(context, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to The Big 6ix", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { launcher.launch(googleSignInClient.signInIntent) }) {
            Text("Sign in with Google")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Privacy Policy",
            modifier = Modifier.clickable {
                val url = "https://thebig6ix.co.uk/the-big-6ix-privacy-policy/"
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

suspend fun verifyYouTubeMembershipViaCloudFunction(
    context: Context,
    authCode: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://us-central1-the-big-6ix.cloudfunctions.net/exchangeAuthCodeForTokenAndCheckMembership")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        val jsonBody = JSONObject().put("authCode", authCode).toString()
        OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val code = conn.responseCode
        conn.disconnect()

        if (code == 200) {
            withContext(Dispatchers.Main) { onSuccess() }
        } else {
            val message = JSONObject(response).optString("message", "Access denied")
            withContext(Dispatchers.Main) { onFailure(message) }
        }
    } catch (e: Exception) {
        Log.e("CloudFunction", "Error: ${e.message}")
        withContext(Dispatchers.Main) { onFailure("Membership check failed: ${e.message}") }
    }
}
