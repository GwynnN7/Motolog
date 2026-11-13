package com.gwynn7.motolog.rides.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gwynn7.motolog.HomeNavDirections
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gwynn7.motolog.R
import com.gwynn7.motolog.databinding.RideRowBinding
import com.gwynn7.motolog.databinding.RidesListBinding
import com.gwynn7.motolog.rides.data.Ride
import com.gwynn7.motolog.rides.data.RidePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class RidesFragment : Fragment(R.layout.rides_list) {
    private val model: RidesViewModel by viewModels()
    private var binding: RidesListBinding? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = RidesListBinding.bind(view).also { binding = it }
        val adapter = RideAdapter(viewLifecycleOwner, model::preview) { ride ->
            if (ride.activeSlot != null) findNavController().navigate(R.id.open_ride_recorder)
            else findNavController().navigate(HomeNavDirections.openRideDetails(ride.id))
        }
        ui.rides.layoutManager = LinearLayoutManager(requireContext())
        ui.rides.adapter = adapter
        ui.startRide.setOnClickListener { findNavController().navigate(R.id.open_ride_recorder) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.rides.collect { rides -> ui.emptyState.isVisible = rides.isEmpty(); adapter.submitList(rides) }
            }
        }
    }
    override fun onDestroyView() { binding?.rides?.adapter = null; binding = null; super.onDestroyView() }
}

private class RideAdapter(
    private val owner: LifecycleOwner,
    private val preview: (String) -> Flow<List<RidePoint>>,
    private val onClick: (Ride) -> Unit
) : ListAdapter<Ride, RideAdapter.Holder>(object : DiffUtil.ItemCallback<Ride>() {
    override fun areItemsTheSame(oldItem: Ride, newItem: Ride) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Ride, newItem: Ride) = oldItem == newItem
}) {
    class Holder(val ui: RideRowBinding) : RecyclerView.ViewHolder(ui.root) {
        var ride: Ride? = null
        var previewJob: Job? = null
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(RideRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)).also { holder ->
            holder.ui.routePreview.attach(owner, null, previewMode = true)
            holder.ui.routePreview.setOnClickListener { holder.ride?.let(onClick) }
        }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ride = getItem(position)
        if (holder.ride?.id != ride.id) {
            stopPreview(holder)
            holder.ui.routePreview.showRoute(emptyList())
        }
        holder.ride = ride
        with(holder.ui) {
            bike.text = ride.motorcycleName
            date.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ride.startedAt))
            metrics.text = "${rideDistance(ride.distanceMeters, ride.odometerUnit)}  ·  ${rideDuration(ride.durationMillis)}"
            status.setText(when {
                ride.activeSlot != null -> R.string.ride_resume
                ride.mileageDecision == Ride.PENDING -> R.string.ride_mileage_pending
                else -> R.string.ride_summary
            })
            root.setOnClickListener { onClick(ride) }
        }
        if (holder.itemView.isAttachedToWindow) startPreview(holder)
    }

    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        startPreview(holder)
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        stopPreview(holder)
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: Holder) {
        stopPreview(holder)
        holder.ride = null
        holder.ui.routePreview.showRoute(emptyList())
        super.onViewRecycled(holder)
    }

    private fun startPreview(holder: Holder) {
        if (holder.previewJob?.isActive == true) return
        val id = holder.ride?.id ?: return
        holder.previewJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preview(id).collect { points ->
                    if (holder.ride?.id == id) holder.ui.routePreview.showRoute(points)
                }
            }
        }
    }

    private fun stopPreview(holder: Holder) {
        holder.previewJob?.cancel()
        holder.previewJob = null
    }
}
