package com.invenium.thebig6ix.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.invenium.thebig6ix.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val view = binding.root


        val currentUser = auth.currentUser
        val userEmail = currentUser?.email

        // Fetch and set user's display name
        val username = currentUser?.displayName ?: "Unknown User"
        binding.profileTextView.text = username

        // Set the night mode to follow the system's setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        binding.profileImageView.setOnClickListener {
            showImageOptions()
        }

        return view
    }

    private fun showImageOptions() {
        val options = arrayOf("Remove Image", "Choose from Gallery")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Profile Picture")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> removeProfileImage()
                1 -> chooseProfileImage()
            }
        }
        builder.show()
    }

    private fun removeProfileImage() {
        // Implement logic to remove profile image
    }

    private fun chooseProfileImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val selectedImageUri = data.data
            // Set selected image to ImageView
            binding.profileImageView.setImageURI(selectedImageUri)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_IMAGE_PICK = 100
    }
}
