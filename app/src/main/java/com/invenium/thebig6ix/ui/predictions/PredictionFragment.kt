package com.invenium.thebig6ix.ui.predictions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.data.FootballFixture
import com.invenium.thebig6ix.databinding.FragmentPredictionBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PredictionFragment : Fragment() {

    private var _binding: FragmentPredictionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PredictionViewModel
    private lateinit var fixtureList: MutableList<FootballFixture>

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PredictionViewModel::class.java]

        // Initialize the fixture list
        fixtureList = mutableListOf()

        // Set up the Spinner
        setupSpinner()

        // Fetch fixtures from Firebase using coroutine
        lifecycleScope.launch {
            fetchFixtures()
        }

        binding.submitPredictionButton.setOnClickListener {
            val homeGoals = binding.goalsHomeTeamEditText.text.toString()
            val awayGoals = binding.goalsAwayTeamEditText.text.toString()
            val selectedFixture = binding.fixtureSpinner.selectedItem as? FootballFixture
            val userId = FirebaseAuth.getInstance().currentUser?.uid

            if (homeGoals.isNotBlank() && awayGoals.isNotBlank() && selectedFixture != null && userId != null) {
                viewModel.submitPrediction(
                    homeGoals.toInt(),
                    awayGoals.toInt(),
                    selectedFixture,
                    userId,
                    onSuccess = {
                        Toast.makeText(context, "Prediction submitted!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(context, "Failed to submit prediction", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        val isAdmin = currentUser?.email == "ugurdenli30@gmail.com" // Replace with your actual admin email

        binding.updatePointsButton.visibility = if (isAdmin) View.VISIBLE else View.GONE

        binding.updatePointsButton.setOnClickListener {
            viewModel.updatePointsForCompletedFixtures()
        }
    }

    private fun setupSpinner() {
        val spinnerAdapter = ArrayAdapter<FootballFixture>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fixtureList
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fixtureSpinner.adapter = spinnerAdapter
    }

    private suspend fun fetchFixtures() {
        try {
            val fixturesSnapshot = db.collection("fixtures").get().await()

            fixtureList.clear()

            for (document in fixturesSnapshot.documents) {
                val homeTeam = document.getString("homeTeam") ?: continue
                val awayTeam = document.getString("awayTeam") ?: continue
                val date = document.getString("date") ?: continue
                val id = document.id // Use the document ID as a unique identifier

                val fixture = FootballFixture(id, homeTeam, awayTeam, date,
                    homeTeamGoals = document.getLong("homeTeamGoals")?.toInt() ?: -1,
                    awayTeamGoals = document.getLong("awayTeamGoals")?.toInt() ?: -1,
                    winner = document.getString("winner") ?: ""
                )
                fixtureList.add(fixture)
            }

            // Update spinner adapter after fetching data
            (binding.fixtureSpinner.adapter as ArrayAdapter<FootballFixture>).notifyDataSetChanged()

        } catch (e: Exception) {
            Toast.makeText(context, "Error fetching fixtures: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
