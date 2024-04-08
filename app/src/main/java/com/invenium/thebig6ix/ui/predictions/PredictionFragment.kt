package com.invenium.thebig6ix.ui.predictions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.invenium.thebig6ix.databinding.FragmentDashboardBinding

class PredictionFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var predictionViewModel: PredictionViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        predictionViewModel = ViewModelProvider(this).get(PredictionViewModel::class.java)

        predictionViewModel.footballFixtures.observe(viewLifecycleOwner, Observer { fixtures ->
            // Update your UI here with the fetched football fixtures
            val fixturesText = fixtures.joinToString("\n") { "${it.homeTeam} vs ${it.awayTeam} on ${it.date}" }
            binding.textDashboard.text = fixturesText
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}