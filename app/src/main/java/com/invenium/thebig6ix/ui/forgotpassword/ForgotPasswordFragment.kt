package com.invenium.thebig6ix.ui.forgotpassword

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.R

class ForgotPasswordFragment : Fragment() {

    private lateinit var emailEditText: TextInputEditText
    private lateinit var resetPasswordButton: Button

    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_forgot_password, container, false)
        initializeViews(view)
        setupClickListener()
        return view
    }

    private fun initializeViews(view: View) {
        emailEditText = view.findViewById(R.id.editTextEmail)
        resetPasswordButton = view.findViewById(R.id.buttonResetPassword)
    }

    private fun setupClickListener() {
        resetPasswordButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isNotEmpty()) {
                // Send reset password email
                firebaseAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        // Show a dialog confirming email sent
                        showConfirmationDialog()
                    }
                    .addOnFailureListener { e ->
                        // Show an error message
                        Toast.makeText(context, "Failed to send reset password email", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Show an error message if email is empty
                Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_confirmation, null)
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
        val dialog = dialogBuilder.create()
        dialog.show()

        val buttonOk = dialogView.findViewById<Button>(R.id.buttonOk)
        buttonOk.setOnClickListener {
            findNavController().navigate(R.id.action_forgotPasswordFragment_to_navigation_login)
            dialog.dismiss()
        }
    }
}
