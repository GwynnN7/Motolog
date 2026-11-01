package com.gwynn7.motolog.Fragments.ModsLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.ModslogRowBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.repairColors

class ModsLogAdapter(
    private val onLogClicked: (ModsLog) -> Unit
) : ListAdapter<ModsLog, ModsLogAdapter.ViewHolder>(DiffCallback()) {
    class ViewHolder(val binding: ModslogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<ModsLog>() {
        override fun areItemsTheSame(oldItem: ModsLog, newItem: ModsLog) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ModsLog, newItem: ModsLog) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ModslogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = getItem(position)
        with(holder.binding) {
            twModTitle.text = currentItem.title
            twModTitle.isSelected = true
            twModDescription.text = currentItem.description
            twModDescription.isSelected = true
            twModDate.text = longToDateString(currentItem.date)
            twModPrice.text = root.context.getString(R.string.price) + ": %.2f%s".format(
                currentItem.price, UnitHelper.getCurrency()
            )
            modImage.setColorFilter(ContextCompat.getColor(root.context, repairColors[position % repairColors.size]))
            cvModsRow.setOnClickListener { onLogClicked(currentItem) }
        }
    }
}
