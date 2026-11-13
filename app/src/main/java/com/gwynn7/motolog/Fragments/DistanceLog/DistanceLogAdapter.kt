package com.gwynn7.motolog.Fragments.DistanceLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.DistancelogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.ui.calendarDaysBetween
import com.gwynn7.motolog.ui.formatLogInterval

class DistanceLogAdapter(
    private var startKm: Int,
    private val onLogClicked: (DistanceLog) -> Unit
) : ListAdapter<DistanceLog, DistanceLogAdapter.ViewHolder>(DiffCallback()) {
    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    class ViewHolder(val binding: DistancelogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<DistanceLog>() {
        override fun areItemsTheSame(oldItem: DistanceLog, newItem: DistanceLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DistanceLog, newItem: DistanceLog) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DistancelogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = getItem(position)
        with(holder.binding) {
            twDistanceLog.text = "${formatThousand(currentItem.distance)} ${UnitHelper.getDistance()}"
            twDistanceDate.text = longToDateString(currentItem.date)

            val previous = currentList.getOrNull(position + 1)
            val difference = formatThousand(currentItem.distance - (previous?.distance ?: startKm))
            val days = previous?.let { calendarDaysBetween(it.date, currentItem.date) }
            twDistanceDifference.text = when {
                days == null -> root.context.getString(R.string.log_distance_initial, difference, UnitHelper.getDistance())
                days <= 0 -> root.context.getString(R.string.log_distance_same_day, difference, UnitHelper.getDistance())
                else -> root.context.getString(R.string.log_distance_change,
                    difference, UnitHelper.getDistance(), root.context.formatLogInterval(days))
            }

            cvDistanceRow.setOnClickListener { onLogClicked(currentItem) }
            editLog.setOnClickListener { onLogClicked(currentItem) }
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<DistanceLog>, currentList: MutableList<DistanceLog>) {
        super.onCurrentListChanged(previousList, currentList)
        // A neighbour's edit/deletion also changes this row's distance increment.
        val previousReadings = previousList.mapIndexed { index, log -> log.id to previousList.getOrNull(index + 1)?.let { it.distance to it.date } }.toMap()
        for (index in currentList.indices) {
            val id = currentList[index].id
            if (id in previousReadings && previousReadings[id] != currentList.getOrNull(index + 1)?.let { it.distance to it.date }) {
                notifyItemChanged(index)
            }
        }
    }

    fun updateStartKm(value: Int) {
        if (startKm == value) return
        startKm = value
        // Only the oldest entry compares against the starting odometer.
        if (itemCount > 0) notifyItemChanged(itemCount - 1)
    }
}
