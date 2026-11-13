package com.gwynn7.motolog.Fragments.Garage

import android.view.LayoutInflater
import androidx.core.view.isVisible
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.photos.PhotoLoader
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.MotorcycleRowBinding
import com.gwynn7.motolog.formatThousand

class MotorcycleListAdapter : ListAdapter<Motorcycle, MotorcycleListAdapter.ViewHolder>(DiffCallback()) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

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

            twBikeModel.text = currentItem.model

            twBikeAlias.text = currentItem.alias
            twBikeAlias.isVisible = currentItem.alias.isNotBlank() && currentItem.alias != currentItem.model
            twBikeYear.text = String.format("${getString(root.context, R.string.year)}: %d", currentItem.year)

            val distance = root.context.getString(R.string.profile_odometer_value, formatThousand(currentItem.start_km + currentItem.personal_km), UnitHelper.getDistance())
            twBikeDistance.text = distance

            PhotoLoader.load(motorcycleImage, currentItem.listImage, R.drawable.bike, 400)

            cvBikeRow.setOnClickListener {
                root.findNavController().navigate(MotorcycleListFragmentDirections.bikelistToBikehome(currentItem.id))
            }

            editProfile.setOnClickListener {
                root.findNavController().navigate(MotorcycleListFragmentDirections.bikelistToBikeadd(currentItem))
            }
            cvBikeRow.setOnLongClickListener {
                val action = MotorcycleListFragmentDirections.bikelistToBikeadd(currentItem)
                root.findNavController().navigate(action)
                true
            }
        }
    }
    override fun onViewRecycled(holder: ViewHolder) {
        PhotoLoader.clear(holder.binding.motorcycleImage)
        super.onViewRecycled(holder)
    }
}
