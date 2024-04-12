package com.invenium.thebig6ix.ui.createaccount

import android.app.AlertDialog
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.invenium.thebig6ix.R

class CreateAccountFragment : Fragment() {

    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var createAccountButton: Button

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_create_account, container, false)
        initializeViews(view)
        setupClickListener()
        setupInputValidations()
        return view
    }

    private fun initializeViews(view: View) {
        nameEditText = view.findViewById(R.id.editTextName)
        emailEditText = view.findViewById(R.id.editTextEmail)
        passwordEditText = view.findViewById(R.id.editTextPassword)
        confirmPasswordEditText = view.findViewById(R.id.editTextConfirmPassword)
        createAccountButton = view.findViewById(R.id.buttonCreateAccount)
    }

    private fun setupClickListener() {
        createAccountButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (password != confirmPassword) {
                // Handle password mismatch error
                confirmPasswordEditText.error = "Passwords do not match"
                return@setOnClickListener
            }

            // Create user with email and password
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // User registration successful
                        val user = firebaseAuth.currentUser

                        // Send email verification
                        user?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                            if (emailTask.isSuccessful) {
                                // Email verification sent successfully
                                showEmailConfirmationDialog()
                                // Proceed to store user data in Firestore
                                val userId = user.uid
                                val userData = hashMapOf(
                                    "fullName" to name,
                                    "email" to email
                                    // Add more fields if needed
                                )
                                firebaseFirestore.collection("users").document(userId)
                                    .set(userData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        // Data successfully written to Firestore
                                        // You can navigate to another screen or perform any other action
                                        // For example, navigate to the login screen
                                        navigateToLoginScreen()
                                    }
                                    .addOnFailureListener { e ->
                                        // Error writing data to Firestore
                                    }
                            } else {
                                // Failed to send email verification
                                // Handle the failure here
                            }
                        }
                    } else {
                        // User registration failed
                        // Handle the error here
                    }
                }
        }
    }

    private fun setupInputValidations() {
        emailEditText.doAfterTextChanged {
            if (!isValidEmail(it.toString().trim())) {
                emailEditText.error = "Enter a valid email address"
            } else {
                emailEditText.error = null
            }
        }

        passwordEditText.doAfterTextChanged {
            if (!isValidPassword(it.toString())) {
                passwordEditText.error = "Password must be at least 6 characters long and contain a number and a special character"
            } else {
                passwordEditText.error = null
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[0-9])(?=.*[!@#\$%^&*])(?=\\S+\$).{6,}\$".toRegex()
        return passwordPattern.matches(password)
    }

    private fun showEmailConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_confirmation, null)
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val dialog = dialogBuilder.create()
        dialog.show()

        val buttonOk = dialogView.findViewById<Button>(R.id.buttonOk)
        buttonOk.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun navigateToLoginScreen() {
        // Navigate to the login screen or any other screen in your app
        // For example:
        findNavController().navigate(R.id.action_createAccountFragment_to_navigation_login)
    }
}
