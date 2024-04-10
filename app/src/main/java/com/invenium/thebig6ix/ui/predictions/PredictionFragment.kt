package com.invenium.thebig6ix.ui.predictions

import android.R
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.databinding.FragmentPredictionBinding

class PredictionFragment : Fragment() {

    private var _binding: FragmentPredictionBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val predictionsCollection = db.collection("predictions")
    private val fixtureList = mutableListOf<String>() // List to store fixture names
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPredictionBinding.inflate(inflater, container, false)
        val view = binding.root

        // Initialize spinner
        spinnerAdapter = ArrayAdapter(requireContext(), R.layout.simple_spinner_item, fixtureList)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.fixtureSpinner.adapter = spinnerAdapter

        // Populate spinner with fixture data from Firestore
        retrieveFixtureData()

        binding.submitPredictionButton.setOnClickListener {
            val homeTeamGoals = binding.goalsHomeTeamEditText.text.toString()
            val awayTeamGoals = binding.goalsAwayTeamEditText.text.toString()

            if (homeTeamGoals.isNotBlank() && awayTeamGoals.isNotBlank()) {
                submitPrediction(homeTeamGoals.toInt(), awayTeamGoals.toInt())
            }
        }

        return view
    }

    private fun retrieveFixtureData() {
        db.collection("fixtures")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val homeTeam = document.getString("homeTeam") ?: ""
                    val awayTeam = document.getString("awayTeam") ?: ""
                    val date = document.getString("date") ?: ""
                    val time = document.getString("time") ?: ""
                    val fixtureName = "$homeTeam vs $awayTeam - $date, $time"
                    fixtureList.add(fixtureName)
                }
                // Notify adapter that data set has changed
                spinnerAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                // Handle failure
                Log.e(TAG, "Error retrieving fixture data: $exception")
            }
    }

    private fun submitPrediction(homeTeamGoals: Int, awayTeamGoals: Int) {
        val prediction = hashMapOf(
            "home_team_goals" to homeTeamGoals,
            "away_team_goals" to awayTeamGoals
        )

        predictionsCollection.add(prediction)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Prediction added with ID: ${documentReference.id}")
                // Show a success message or navigate to another screen
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error adding prediction", exception)
                // Show an error message to the user
                Toast.makeText(context, "Failed to submit prediction", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
