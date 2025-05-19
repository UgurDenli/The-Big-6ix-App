package com.invenium.thebig6ix.ui.login

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    // Initialize views (assuming you have these IDs in your XML)
    private lateinit var signInButton: Button
    private lateinit var privacyPolicyTextView: TextView

    companion object {
        private const val TAG = "LoginFragment"
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        signInButton = view.findViewById(R.id.buttonGoogleSignIn)
        privacyPolicyTextView = view.findViewById(R.id.textViewPrivacyPolicy)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web))
            .requestServerAuthCode(getString(R.string.default_web), true) // Get OAuth Token
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/youtube.channel-memberships.creator"),
                Scope("https://www.googleapis.com/auth/youtube.readonly")
            )
            .build()


        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                Log.w(TAG, "Google Sign-In failed, result code: ${result.resultCode}")
            }
        }

        signInButton.setOnClickListener { signIn() }
        privacyPolicyTextView.setOnClickListener { openPrivacyPolicy() }
    }

    private fun signIn() {
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=${e.statusCode}", e)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                    val serverAuthCode = account?.serverAuthCode

                    if (!serverAuthCode.isNullOrEmpty()) {
                        exchangeAuthCodeForAccessToken(serverAuthCode)
                    } else {
                        Log.e(TAG, "Google Access Token is null.")
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
            }
    }
    private fun exchangeAuthCodeForAccessToken(authCode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://oauth2.googleapis.com/token")
                val postData = "code=$authCode" +
                        "&client_id=${getString(R.string.default_web)}" +
                        "&client_secret=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" +
                        "&redirect_uri=" +
                        "&grant_type=authorization_code"

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val outputStream = connection.outputStream
                outputStream.write(postData.toByteArray(Charsets.UTF_8))
                outputStream.flush()
                outputStream.close()

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val accessToken = jsonResponse.optString("access_token", "")

                if (accessToken.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        checkYouTubeMembership(auth.currentUser!!.uid, accessToken)
                    }
                } else {
                    Log.e(TAG, "Failed to fetch access token.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exchanging auth code for access token: ${e.message}")
            }
        }
    }


    private fun checkYouTubeMembership(userId: String, accessToken: String) {
        fetchAllSubscriptions(accessToken) { channelIds ->
            val isMember = channelIds.contains("UCUP5RcljxXkKm3agi9WNrDA")
            if (isMember) {
                val db = FirebaseFirestore.getInstance()
                val userRef = db.collection("users").document(userId)
                userRef.get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            checkOnboardingStatus(userId)
                        } else {
                            createUserDocument(userId)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error getting user document", e)
                    }
            } else {
                auth.signOut()
            }
        }
    }

    private fun fetchAllSubscriptions(accessToken: String, completion: (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val allChannelIds = mutableListOf<String>()
            var nextPageToken: String? = null

            do {
                val urlString =
                    "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50${nextPageToken?.let { "&pageToken=$it" } ?: ""}"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessToken")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val jsonResponse = JSONObject(responseText)
                        val itemsArray = jsonResponse.getJSONArray("items")
                        for (i in 0 until itemsArray.length()) {
                            val channelId = itemsArray.getJSONObject(i)
                                .getJSONObject("snippet")
                                .getJSONObject("resourceId")
                                .getString("channelId")
                            allChannelIds.add(channelId)
                        }
                        nextPageToken = jsonResponse.optString("nextPageToken", null)
                    } catch (e: JSONException) {
                        Log.e(TAG, "Error parsing JSON response: ${e.message}")
                    }
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    Log.e(
                        TAG,
                        "HTTP error code: 401 - Unauthorized. Please check the access token."
                    )
                    // Handle token refresh logic here if needed
                } else {
                    Log.e(TAG, "HTTP error code: $responseCode")
                }
                connection.disconnect()
            } while (nextPageToken != null)

            withContext(Dispatchers.Main) {
                completion(allChannelIds)
            }
        }
    }
    private fun checkOnboardingStatus(userId: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val completedOnboarding = document.getBoolean("completedOnboarding") ?: false
                    if (completedOnboarding) {
                        navigateToHomeView()
                    } else {
                        navigateToOnboardingView()
                    }
                } else {
                    Log.d(TAG, "Error: User document does not exist.")
                    Toast.makeText(
                        requireContext(),
                        "Error: User document does not exist.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error checking onboarding status", e)
                Toast.makeText(
                    requireContext(),
                    "Error checking onboarding status",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    private fun openPrivacyPolicy() {
        val url = "https://thebig6ix.co.uk/the-big-6ix-privacy-policy/"
        CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(url))
    }
    private fun navigateToHomeView() {
        findNavController().navigate(R.id.action_navigation_login_to_navigation_home)
        Toast.makeText(requireContext(), "Navigating to Home", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToOnboardingView() {
        findNavController().navigate(R.id.action_navigation_login_to_navigation_home)
        Toast.makeText(requireContext(), "Navigating to Onboarding", Toast.LENGTH_SHORT).show()
    }

    private fun createUserDocument(userId: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        val userData = hashMapOf(
            "fullName" to (auth.currentUser?.displayName ?: ""),
            "email" to (auth.currentUser?.email ?: ""),
            "score" to 0,
            "completedOnboarding" to false,
            "generalEmailsEnabled" to true,
            "personalizedEmailsEnabled" to true,
            "weeklyScore" to 0,
            "monthlyScore" to 0
        )

        userRef.set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "User document created in Firestore successfully.")
                navigateToOnboardingView()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error creating user document", e)
                Toast.makeText(
                    requireContext(),
                    "Error creating user document",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}

