package com.gwynn7.motolog.Fragments.RepairsLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.RepairslogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString

class RepairsLogAdapter(
    private val onLogClicked: (RepairsLog) -> Unit,
    private val onEmptyChanged: (Boolean) -> Unit = {}
) : ListAdapter<RepairsLog, RepairsLogAdapter.ViewHolder>(DiffCallback()) {
    private var selectedType = -1
    private var oldestFirst = false
    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

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
            repairImage.setImageResource(repairCategoryIcon(currentItem.typeIndex))
            repairCategory.text = root.resources.getStringArray(R.array.repair_types).getOrElse(currentItem.typeIndex) { root.context.getString(R.string.type) }
            twRepairNotes.visibility = if (currentItem.notes.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            editLog.setOnClickListener { onLogClicked(currentItem) }
            twRepairPrice.text = root.context.getString(R.string.price) + ": %.2f%s".format(
                currentItem.price, UnitHelper.getCurrency()
            )
            twRepairDistance.text = "${formatThousand(currentItem.repair_km)} ${UnitHelper.getDistance()}"
            cvRepairsRow.setOnClickListener { onLogClicked(currentItem) }
        }
    }

    fun submitLogs(logs: List<RepairsLog>) {
        allLogs = logs
        filter(selectedType, oldestFirst)
    }

    fun filter(repairTypeIndex: Int, oldestFirst: Boolean) {
        selectedType = repairTypeIndex
        this.oldestFirst = oldestFirst
        val logs = allLogs
            .filter { repairTypeIndex == NO_FILTER || it.typeIndex == repairTypeIndex }
            .let { if (oldestFirst) it.reversed() else it }
        submitList(logs)
    }

    override fun onCurrentListChanged(previousList: MutableList<RepairsLog>, currentList: MutableList<RepairsLog>) {
        onEmptyChanged(currentList.isEmpty())
    }

    private companion object {
        const val NO_FILTER = -1
    }
}
