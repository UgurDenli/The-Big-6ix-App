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
        fixtureList = mutableListOf()

        setupSpinner()

        lifecycleScope.launch {
            fetchFixtures()
        }

        binding.submitPredictionButton.setOnClickListener {
            val homeGoals = binding.goalsHomeTeamEditText.text.toString()
            val awayGoals = binding.goalsAwayTeamEditText.text.toString()
            val selectedFixture = binding.fixtureSpinner.selectedItem as? FootballFixture

            if (homeGoals.isNotBlank() && awayGoals.isNotBlank() && selectedFixture != null) {
                viewModel.submitPrediction(
                    fixtureId = selectedFixture.id,
                    homeGoals = homeGoals.toInt(),
                    awayGoals = awayGoals.toInt()
                )
                Toast.makeText(context, "Prediction submitted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val isAdmin = currentUser?.email == "ugurdenli30@gmail.com"
        binding.updatePointsButton.visibility = if (isAdmin) View.VISIBLE else View.GONE

        binding.updatePointsButton.setOnClickListener {
            viewModel.updatePointsForCompletedFixtures()
        }
    }

    private fun setupSpinner() {
        val spinnerAdapter = ArrayAdapter(
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
                val id = document.id

                val fixture = FootballFixture(
                    id = id,
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    date = date,
                    homeTeamGoals = document.getLong("homeTeamGoals")?.toInt() ?: -1,
                    awayTeamGoals = document.getLong("awayTeamGoals")?.toInt() ?: -1,
                    winner = document.getString("winner") ?: ""
                )
                fixtureList.add(fixture)
            }

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
