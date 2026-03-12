package com.gwynn7.motolog.Fragments.RepairsLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.RepairslogRowBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.repairColors

class RepairsLogAdapter : ListAdapter<RepairsLog, RepairsLogAdapter.ViewHolder>(DiffCallback()) {
    private lateinit var currentBike: Motorcycle

    class ViewHolder(val binding: RepairslogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<RepairsLog>() {
        override fun areItemsTheSame(oldItem: RepairsLog, newItem: RepairsLog) =
            oldItem.date == newItem.date && oldItem.typeText == newItem.typeText
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

            repairImage.setColorFilter(ContextCompat.getColor(root.context, repairColors[currentItem.typeIndex]))

            val price = String.format("${root.resources.getString(R.string.price)}: %.2f%s", currentItem.price, UnitHelper.getCurrency())
            twRepairPrice.text = price

            val distance = String.format("%s %s", formatThousand(currentItem.repair_km), UnitHelper.getDistance())
            twRepairDistance.text = distance

            cvRepairsRow.setOnClickListener {
                var arrayType = currentItem.typeIndex
                MaterialAlertDialogBuilder(root.context)
                    .setNegativeButton(R.string.back, null)
                    .setSingleChoiceItems(root.resources.getStringArray(R.array.repair_types), currentItem.typeIndex) { _, which ->
                        arrayType = which
                    }
                    .setTitle(R.string.choose_repair_type)
                    .setPositiveButton(R.string.edit) { _, _ ->
                        val action = RepairsLogFragmentDirections.repairslistToRepairsadd(currentBike, arrayType, position)
                        root.findNavController().navigate(action)
                    }
                    .show()
            }
        }
    }

    fun filter(repairTypeIndex: Int, reverse: Boolean) {
        var filtered = currentBike.logs.maintenance.filter { it.typeIndex == repairTypeIndex || repairTypeIndex == -1 }
        filtered = filtered.sortedByDescending { it.date }
        if (reverse) filtered = filtered.reversed()
        submitList(filtered)
    }

    fun bindBike(bike: Motorcycle) {
        currentBike = bike
        submitList(bike.logs.maintenance)
    }
}
