package com.gwynn7.motolog.Fragments.Garage

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.core.net.toFile
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.BikeActivity
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.MotorcycleRowBinding
import com.gwynn7.motolog.formatThousand

class MotorcycleListAdapter : ListAdapter<Motorcycle, MotorcycleListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: MotorcycleRowBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Motorcycle>() {
        override fun areItemsTheSame(oldItem: Motorcycle, newItem: Motorcycle) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Motorcycle, newItem: Motorcycle) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MotorcycleRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItem = getItem(position)
        with(holder.binding) {
            twBikeManufacturer.text = currentItem.manufacturer

            twBikeModel.isSelected = true
            twBikeModel.text = currentItem.model

            twBikeAlias.text = currentItem.alias
            twBikeYear.text = String.format("${getString(root.context, R.string.year)}: %d", currentItem.year)

            val distance = String.format("%s %s", formatThousand(currentItem.personal_km), UnitHelper.getDistance())
            twBikeDistance.text = distance

            if (currentItem.listImage != null && currentItem.listImage!!.toFile().exists()) {
                motorcycleImage.setImageURI(currentItem.listImage)
            } else {
                motorcycleImage.setImageResource(R.drawable.bike)
            }

            cvBikeRow.setOnClickListener {
                val bikeActivity = Intent(root.context, BikeActivity::class.java)
                bikeActivity.putExtra("bike_id", currentItem.id)
                root.context.startActivity(bikeActivity)
            }

            cvBikeRow.setOnLongClickListener {
                val action = MotorcycleListFragmentDirections.bikelistToBikeadd(currentItem)
                root.findNavController().navigate(action)
                true
            }
        }
    }
}
