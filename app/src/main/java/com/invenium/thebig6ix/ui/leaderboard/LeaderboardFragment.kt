package com.invenium.thebig6ix.ui.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.LeaderboardItem
import com.invenium.thebig6ix.databinding.FragmentLeaderboardBinding

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val leaderboardCollection = db.collection("leaderboard")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        val view = binding.root

        // Set up RecyclerView
        val leaderboardAdapter = LeaderboardAdapter()
        binding.fullLeaderboardRecyclerView.apply {
            adapter = leaderboardAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }

        // Retrieve leaderboard data from Firestore
        retrieveLeaderboardData(leaderboardAdapter)

        return view
    }

    private fun retrieveLeaderboardData(leaderboardAdapter: LeaderboardAdapter) {
        leaderboardCollection.get()
            .addOnSuccessListener { result ->
                val leaderboardList = mutableListOf<LeaderboardItem>()
                for (document in result) {
                    val score = document.getDouble("score") ?: 0.0
                    val username = document.getString("username") ?: ""
                    val leaderboardItem = LeaderboardItem(username, score)
                    leaderboardList.add(leaderboardItem)
                }
                // Sort the leaderboard list by score in descending order
                val sortedLeaderboardList = leaderboardList.sortedByDescending { it.score }
                // Update the RecyclerView with the sorted data
                leaderboardAdapter.submitList(sortedLeaderboardList)
            }
            .addOnFailureListener { exception ->
                // Handle failure
                // For example, show an error message
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}