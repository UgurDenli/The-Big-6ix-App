package com.invenium.thebig6ix.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DiscordAuthViewModel : ViewModel() {

    sealed class LoginState {
        object Idle : LoginState()
        object Loading : LoginState()
        data class Success(val needsOnboarding: Boolean) : LoginState()
        data class Error(val message: String) : LoginState()
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun onDiscordCallback(token: String, name: String) {
        if (_loginState.value is LoginState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val auth = FirebaseAuth.getInstance()
                auth.signInWithCustomToken(token).await()
                val firebaseUser = auth.currentUser
                    ?: throw Exception("No user after sign-in")

                val db = FirebaseFirestore.getInstance()
                val userDoc = db.collection("users").document(firebaseUser.uid)
                val snapshot = userDoc.get().await()

                val needsOnboarding: Boolean
                if (snapshot.exists()) {
                    userDoc.set(mapOf("fullName" to name), SetOptions.merge()).await()
                    needsOnboarding = snapshot.getBoolean("completedOnboarding") != true
                } else {
                    userDoc.set(mapOf(
                        "fullName" to name,
                        "score" to 0,
                        "weeklyScore" to 0,
                        "monthlyScore" to 0,
                        "completedOnboarding" to false
                    )).await()
                    needsOnboarding = true
                }

                _loginState.value = LoginState.Success(needsOnboarding)
            } catch (e: Exception) {
                Log.e("DiscordAuthViewModel", "Sign-in failed", e)
                _loginState.value = LoginState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun onDiscordError(error: String) {
        _loginState.value = LoginState.Error(error)
    }

    fun clearState() {
        _loginState.value = LoginState.Idle
    }
}
