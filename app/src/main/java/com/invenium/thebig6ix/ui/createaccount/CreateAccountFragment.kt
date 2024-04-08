package com.invenium.thebig6ix.ui.createaccount

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
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
                return@setOnClickListener
            }

            // Create user with email and password
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // User registration successful
                        val user = firebaseAuth.currentUser
                        val userId = user?.uid

                        // Store user data in Firestore
                        userId?.let {
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
                                }
                                .addOnFailureListener { e ->
                                    // Error writing data to Firestore
                                }
                        }
                    } else {
                        // User registration failed
                        // Handle the error here
                    }
                }
        }
    }
}
