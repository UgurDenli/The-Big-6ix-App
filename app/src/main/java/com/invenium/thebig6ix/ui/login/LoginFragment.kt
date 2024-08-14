package com.invenium.thebig6ix.ui.login

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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false) // Inflate your layout

        signInButton = view.findViewById(R.id.buttonGoogleSignIn)
        privacyPolicyTextView = view.findViewById(R.id.textViewPrivacyPolicy)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web))
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
            if (result.resultCode == RC_SIGN_IN) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                Log.w(TAG, "Google Sign-In failed, result code: ${result.resultCode}")
            }
        }

        signInButton.setOnClickListener {
            signIn()
        }
        privacyPolicyTextView.setOnClickListener {
            openPrivacyPolicy()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            Log.d(TAG, "firebaseAuthWithGoogle: " + account.id)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        checkYouTubeMembership(it.uid, idToken)
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(requireContext(), "Authentication Failed.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    private fun checkYouTubeMembership(userId: String, accessToken: String) {
        fetchAllSubscriptions(accessToken) { channelIds ->
            val isMember = channelIds.contains("UCUP5RcljxXkKm3agi9WNrDA") // Replace with your YouTube Channel ID
            if (isMember) {
                val db = FirebaseFirestore.getInstance()
                val userRef = db.collection("users").document(userId)
                userRef.get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            Log.d(TAG, "User exists in Firestore.")
                            checkOnboardingStatus(userId)
                        } else {
                            Log.d(TAG, "User doesn't exist in Firestore - creating a new document.")
                            createUserDocument(userId)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Error getting user document", e)
                        Toast.makeText(
                            requireContext(),
                            "Error getting user document",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Log.d(TAG, "You are not a member of the required YouTube channel.")
                Toast.makeText(
                    requireContext(),
                    "You are not a member of the required YouTube channel.",
                    Toast.LENGTH_SHORT
                ).show()
                auth.signOut() // Sign out if not a member
            }
        }
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

    private fun fetchAllSubscriptions(accessToken: String, completion: (List<String>) -> Unit) {
        val allChannelIds = mutableListOf<String>()
        var nextPageToken: String? = null

        do {
            val urlString = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50${nextPageToken?.let { "&pageToken=$it" } ?: ""}"
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
            } else {
                Log.e(TAG, "HTTP error code: $responseCode")
            }
            connection.disconnect()
        } while (nextPageToken != null)

        completion(allChannelIds)
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

    private fun navigateToHomeView() {
        // Implement your navigation to the home activity/fragment
        Toast.makeText(requireContext(), "Navigating to Home", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToOnboardingView() {
        // Implement your navigation to the onboarding activity/fragment
        Toast.makeText(requireContext(), "Navigating to Onboarding", Toast.LENGTH_SHORT).show()
    }

    private fun openPrivacyPolicy() {
        val url = "https://thebig6ix.co.uk/the-big-6ix-privacy-policy/" // Replace with your privacy policy URL
        val builder = CustomTabsIntent.Builder()
        val customTabsIntent = builder.build()
        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
    }
}
