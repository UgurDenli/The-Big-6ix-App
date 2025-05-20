package com.invenium.thebig6ix.ui.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*

@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val users = viewModel.users.value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Leaderboard", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            itemsIndexed(users) { index, user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(text = user.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Points: ${user.totalScore}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

class LeaderboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _users = mutableStateOf(listOf<UserScore>())
    val users: State<List<UserScore>> = _users

    init {
        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        viewModelScope.launch {
            val snapshot = db.collection("users")
                .orderBy("totalScore", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            val userList = snapshot.documents.mapNotNull { doc ->
                doc.getString("name")?.let { name ->
                    UserScore(name, doc.getLong("totalScore")?.toInt() ?: 0)
                }
            }
            _users.value = userList
        }
    }
}

data class UserScore(val name: String, val totalScore: Int)
