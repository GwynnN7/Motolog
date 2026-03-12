package com.gwynn7.motolog.Fragments.ModsLog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.ModslogRowBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.repairColors

class ModsLogAdapter : ListAdapter<ModsLog, ModsLogAdapter.ViewHolder>(DiffCallback()) {
    private lateinit var currentBike: Motorcycle

    class ViewHolder(val binding: ModslogRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<ModsLog>() {
        override fun areItemsTheSame(oldItem: ModsLog, newItem: ModsLog) =
            oldItem.date == newItem.date && oldItem.title == newItem.title
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

            val price = String.format("${root.resources.getString(R.string.price)}: %.2f%s", currentItem.price, UnitHelper.getCurrency())
            twModPrice.text = price

            modImage.setColorFilter(ContextCompat.getColor(root.context, repairColors[position % repairColors.size]))

            cvModsRow.setOnClickListener {
                val action = ModsLogFragmentDirections.modslogToModsadd(currentBike, position)
                root.findNavController().navigate(action)
            }
        }
    }

    fun bindBike(bike: Motorcycle) {
        currentBike = bike
        submitList(bike.logs.mods)
    }
}
