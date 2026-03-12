package com.gwynn7.motolog.Fragments.Gear

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toFile
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.GearRowBinding
import com.gwynn7.motolog.longToDateString

class GearListAdapter : ListAdapter<Gear, GearListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: GearRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Gear>() {
        override fun areItemsTheSame(oldItem: Gear, newItem: Gear) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Gear, newItem: Gear) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = GearRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = getItem(position)
        with(holder.binding) {
            twGearModel.isSelected = true
            twGearModel.text = currentItem.model

            twGearManufacturer.text = currentItem.manufacturer
            twGearDate.text = String.format(
                "${root.resources.getString(R.string.date)}: %s", longToDateString(currentItem.date)
            )

            val price = String.format("%.2f%s", currentItem.price, UnitHelper.getCurrency())
            twGearPrice.text = price

            if (currentItem.listImage != null && currentItem.listImage!!.toFile().exists()) {
                gearImage.setImageURI(currentItem.listImage)
            } else {
                gearImage.setImageResource(R.drawable.helmet_list)
            }

            cvGearRow.setOnClickListener {
                val action = GearListFragmentDirections.gearlistToGearshow(currentItem)
                root.findNavController().navigate(action)
            }

            cvGearRow.setOnLongClickListener {
                val action = GearListFragmentDirections.gearlistToGearadd(currentItem)
                root.findNavController().navigate(action)
                true
            }
        }
    }
}
