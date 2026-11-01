package com.gwynn7.motolog.Fragments.DistanceLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.DistancelogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString

class DistanceLogAdapter(
    private var startKm: Int,
    private val onLogClicked: (DistanceLog) -> Unit
) : ListAdapter<DistanceLog, DistanceLogAdapter.ViewHolder>(DiffCallback()) {
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
            twDistanceLog.isSelected = true
            twDistanceLog.text = "${formatThousand(currentItem.distance)} ${UnitHelper.getDistance()}"
            twDistanceDate.text = longToDateString(currentItem.date)

            val previousDistance = currentList.getOrNull(position + 1)?.distance ?: startKm
            twDistanceDifference.text = "+${formatThousand(currentItem.distance - previousDistance)} ${UnitHelper.getDistance()}"

            cvDistanceRow.setOnClickListener { onLogClicked(currentItem) }
        }
    }

    fun updateStartKm(value: Int) {
        if (startKm == value) return
        startKm = value
        notifyDataSetChanged()
    }
}
