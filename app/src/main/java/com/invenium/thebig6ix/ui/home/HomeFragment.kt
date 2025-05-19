package com.invenium.thebig6ix.ui.home

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.LeaderboardItem
import com.invenium.thebig6ix.databinding.FragmentHomeBinding
import com.invenium.thebig6ix.ui.leaderboard.LeaderboardAdapter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var leaderboardAdapter: LeaderboardAdapter

    private val db = FirebaseFirestore.getInstance()
    private val leaderboardCollection = db.collection("leaderboard")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root

        // Set up RecyclerView
        setupRecyclerView()

        // Retrieve leaderboard data from Firestore
        retrieveLeaderboardData()

        // Set up click listener for the "View Full Leaderboard" button
        binding.viewFullLeaderboardButton.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_leaderboardFragment)
        }
        applyAnimations()

        return view
    }
    private fun applyAnimations() {
        // Fade-in animation for RecyclerView
        val fadeInAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        binding.leaderboardRecyclerView.startAnimation(fadeInAnimation)

        // Scale animation for the button
        val scaleAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.scale)
        binding.viewFullLeaderboardButton.startAnimation(scaleAnimation)
    }
    private fun setupRecyclerView() {
        // Initialize the LinearLayoutManager and set it to the RecyclerView
        val layoutManager = LinearLayoutManager(requireContext())
        binding.leaderboardRecyclerView.layoutManager = layoutManager

        // Set the adapter to the RecyclerView
        leaderboardAdapter = LeaderboardAdapter()
        binding.leaderboardRecyclerView.adapter = leaderboardAdapter
    }

    private fun retrieveLeaderboardData() {
        // Retrieve the top 10 leaderboard data from Firestore
        leaderboardCollection
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                val leaderboardList = mutableListOf<LeaderboardItem>()
                for (document in result) {
                    val fullName = document.getString("fullName") ?: ""
                    val monthlyScore = document.get("monthlyScore") as? Map<Int, Int> ?: emptyMap()
                    val leaderboardItem = LeaderboardItem(fullName, monthlyScore)
                    leaderboardList.add(leaderboardItem)
                }
                // Sort the leaderboard list by score in descending order
                val sortedLeaderboardList = leaderboardList.sortedByDescending { it.monthlyScore.values.sum() }
                leaderboardAdapter.submitList(sortedLeaderboardList)
                // Check if the current user is in the top 10 leaderboard
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid
                var userInTop10 = false
                for (document in result) {
                    if (document.id == userId) {
                        userInTop10 = true
                        break
                    }
                }
                // If the current user is not in the top 10, add them with a default score of 0
                if (!userInTop10 && userId != null) {
                    val newUser = hashMapOf(
                        "username" to (currentUser.displayName ?: ""),
                        "score" to 0.0
                    )
                    leaderboardCollection.document(userId).set(newUser)
                        .addOnSuccessListener {
                            // After adding the new user, retrieve the updated leaderboard data
                            retrieveLeaderboardData()
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to add new user to leaderboard", exception)
                            Toast.makeText(context, "Failed to add new user to leaderboard", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to retrieve top 10 leaderboard data", exception)
                Toast.makeText(context, "Failed to retrieve top 10 leaderboard data", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

