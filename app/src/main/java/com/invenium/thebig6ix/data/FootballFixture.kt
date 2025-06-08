package com.invenium.thebig6ix.data

import com.google.firebase.Timestamp

data class FootballFixture(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val date: String,
    val homeTeamGoals: Int = -1,
    val awayTeamGoals: Int = -1,
    val winner: String,
    val deadline: Timestamp? = null  // <-- NEW

) {
    override fun toString(): String {
        return "$homeTeam vs $awayTeam"
    }
}
