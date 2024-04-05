package com.invenium.thebig6ix.ui.predictions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invenium.thebig6ix.data.ApiHelper
import com.invenium.thebig6ix.data.FootballFixture
import kotlinx.coroutines.launch

class PredictionViewModel : ViewModel() {

    private val footballApiService = ApiHelper.getFootballApiService()

    private val _footballFixtures = MutableLiveData<List<FootballFixture>>()
    val footballFixtures: LiveData<List<FootballFixture>> = _footballFixtures

    init {
        fetchFootballFixtures()
    }

    private fun fetchFootballFixtures() {
        viewModelScope.launch {
            try {
                val response = footballApiService.getFootballFixtures(1) // Specify matchday
                _footballFixtures.value = response.matches
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    private val _text = MutableLiveData<String>().apply {
        value = "This is Leaderboard Fragment"
    }
    val text: LiveData<String> = _text
}
