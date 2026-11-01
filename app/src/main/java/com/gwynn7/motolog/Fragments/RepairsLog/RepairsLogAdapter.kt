package com.gwynn7.motolog.Fragments.RepairsLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.RepairslogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.repairColors

class RepairsLogAdapter(
    private val onLogClicked: (RepairsLog) -> Unit
) : ListAdapter<RepairsLog, RepairsLogAdapter.ViewHolder>(DiffCallback()) {
    private var allLogs: List<RepairsLog> = emptyList()

    class ViewHolder(val binding: RepairslogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<RepairsLog>() {
        override fun areItemsTheSame(oldItem: RepairsLog, newItem: RepairsLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RepairsLog, newItem: RepairsLog) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RepairslogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = getItem(position)
        with(holder.binding) {
            twRepairType.text = currentItem.typeText
            twRepairNotes.text = currentItem.notes
            twRepairNotes.isSelected = true
            twRepairDate.text = longToDateString(currentItem.date)
            repairImage.setColorFilter(
                ContextCompat.getColor(root.context, repairColors[currentItem.typeIndex.coerceIn(repairColors.indices)])
            )
            twRepairPrice.text = root.context.getString(R.string.price) + ": %.2f%s".format(
                currentItem.price, UnitHelper.getCurrency()
            )
            twRepairDistance.text = "${formatThousand(currentItem.repair_km)} ${UnitHelper.getDistance()}"
            cvRepairsRow.setOnClickListener { onLogClicked(currentItem) }
        }
    }

    fun submitLogs(logs: List<RepairsLog>) {
        allLogs = logs
        submitList(logs)
    }

    fun filter(repairTypeIndex: Int, oldestFirst: Boolean) {
        val logs = allLogs
            .filter { repairTypeIndex == NO_FILTER || it.typeIndex == repairTypeIndex }
            .let { if (oldestFirst) it.reversed() else it }
        submitList(logs)
    }

    private companion object {
        const val NO_FILTER = -1
    }
}
