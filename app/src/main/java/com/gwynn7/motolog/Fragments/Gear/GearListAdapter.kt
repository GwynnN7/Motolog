package com.gwynn7.motolog.Fragments.Gear

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.photos.PhotoLoader
import com.gwynn7.motolog.R
import com.gwynn7.motolog.databinding.GearRowBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.ui.formatMoney

class GearListAdapter : ListAdapter<Gear, GearListAdapter.ViewHolder>(DiffCallback()) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

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
            twGearModel.text = currentItem.model

            twGearManufacturer.text = currentItem.manufacturer
            twGearDate.text = root.context.getString(R.string.profile_purchase_date, longToDateString(currentItem.date))

            val price = formatMoney(currentItem.price)
            twGearPrice.text = price

            PhotoLoader.load(gearImage, currentItem.listImage, R.drawable.helmet_list, 400)

            cvGearRow.setOnClickListener {
                val action = GearListFragmentDirections.gearlistToGearshow(currentItem)
                root.findNavController().navigate(action)
            }

            editProfile.setOnClickListener {
                root.findNavController().navigate(GearListFragmentDirections.gearlistToGearadd(currentItem))
            }
            cvGearRow.setOnLongClickListener {
                val action = GearListFragmentDirections.gearlistToGearadd(currentItem)
                root.findNavController().navigate(action)
                true
            }
        }
    }
    override fun onViewRecycled(holder: ViewHolder) {
        PhotoLoader.clear(holder.binding.gearImage)
        super.onViewRecycled(holder)
    }
}
