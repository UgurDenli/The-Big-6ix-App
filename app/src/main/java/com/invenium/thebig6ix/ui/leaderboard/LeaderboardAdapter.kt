package com.invenium.thebig6ix.ui.leaderboard

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.invenium.thebig6ix.R
import com.invenium.thebig6ix.data.LeaderboardItem

class LeaderboardAdapter : ListAdapter<LeaderboardItem, LeaderboardAdapter.ViewHolder>(
    LeaderboardDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.leaderboard_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val leaderboardItem = getItem(position)
        holder.bind(leaderboardItem)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val scoreTextView: TextView = itemView.findViewById(R.id.scoreTextView)

        fun bind(leaderboardItem: LeaderboardItem) {
            usernameTextView.text = leaderboardItem.fullName
            val score = (leaderboardItem.monthlyScore.values.firstOrNull() as? Long)?.toInt() ?: 0
            scoreTextView.text = score.toString()
        }
    }

    private class LeaderboardDiffCallback : DiffUtil.ItemCallback<LeaderboardItem>() {
        override fun areItemsTheSame(oldItem: LeaderboardItem, newItem: LeaderboardItem): Boolean {
            return oldItem == newItem
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: LeaderboardItem, newItem: LeaderboardItem): Boolean {
            return oldItem.fullName == newItem.fullName && oldItem.monthlyScore == newItem.monthlyScore
        }
    }
}