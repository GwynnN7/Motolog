package com.gwynn7.motolog.Fragments.DistanceLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.DistancelogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString

class DistanceLogAdapter(private var currentBike: Motorcycle) : ListAdapter<DistanceLog, DistanceLogAdapter.ViewHolder>(DiffCallback()) {
    class ViewHolder(val binding: DistancelogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<DistanceLog>() {
        override fun areItemsTheSame(oldItem: DistanceLog, newItem: DistanceLog) =
            oldItem.date == newItem.date && oldItem.distance == newItem.distance
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
            twDistanceLog.text = String.format("%s %s", formatThousand(currentItem.distance), UnitHelper.getDistance())

            twDistanceDate.text = longToDateString(currentItem.date)

            val deltaDistance = if (position < currentList.size - 1) {
                currentItem.distance - currentList[position + 1].distance
            } else {
                currentItem.distance - currentBike.start_km
            }
            twDistanceDifference.text = String.format("+%s %s", formatThousand(deltaDistance), UnitHelper.getDistance())

            cvDistanceRow.setOnClickListener {
                val action = DistanceLogFragmentDirections.distancelogToDistanceadd(currentBike, position)
                root.findNavController().navigate(action)
            }
        }
    }
}
