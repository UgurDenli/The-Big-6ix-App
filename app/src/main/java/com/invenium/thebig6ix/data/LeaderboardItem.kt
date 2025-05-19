package com.invenium.thebig6ix.data

data class LeaderboardItem(
    val fullName: String,
    val monthlyScore: Map<Int, Int>
)