package com.gwynn7.motolog.rides.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.gwynn7.motolog.R
import com.gwynn7.motolog.databinding.RideDetailsBinding
import com.gwynn7.motolog.rides.data.Mileage
import com.gwynn7.motolog.rides.data.Ride
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class RideDetailsFragment : Fragment(R.layout.ride_details) {
    private val model: RideDetailsViewModel by viewModels()
    private var binding: RideDetailsBinding? = null
    private var metric = RouteMetric.ROUTE
    private var mileageDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = RideDetailsBinding.bind(view).also { binding = it }
        ui.routeMap.attach(viewLifecycleOwner, savedInstanceState) { message ->
            ui.mapNotice.isVisible = message != null
            if (message != null) ui.mapNotice.setText(message)
        }
        metric = RouteMetric.entries.getOrElse(savedInstanceState?.getInt("metric") ?: 0) { RouteMetric.ROUTE }
        ui.mapMetric.text = getString(metric.label)
        ui.mapMetric.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ride_stats)
                .setSingleChoiceItems(RouteMetric.entries.map { getString(it.label) }.toTypedArray(), metric.ordinal) { dialog, index ->
                    metric = RouteMetric.entries[index]
                    ui.mapMetric.text = getString(metric.label)
                    ui.routeMap.showRoute(model.points.value, metric)
                    dialog.dismiss()
                }.setNegativeButton(R.string.back, null).show()
        }
        ui.confirmMileage.setOnClickListener { confirmMileage() }
        ui.skipMileage.setOnClickListener { model.skipMileage() }
        ui.deleteRide.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ride_delete).setMessage(R.string.ride_delete_body)
                .setNegativeButton(R.string.back, null).setPositiveButton(R.string.delete) { _, _ -> model.delete() }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.ride.collect { render() } }
                launch { model.points.collect { ui.routeMap.showRoute(it, metric); render() } }
                launch { model.busy.collect { render() } }
                launch { model.deleted.collect { if (it) findNavController().navigateUp() } }
                launch { model.error.collect { error -> if (error != null) { Snackbar.make(ui.root, error, Snackbar.LENGTH_LONG).show(); model.clearError() } } }
            }
        }
    }

    private fun render() {
        val ui = binding ?: return
        val ride = model.ride.value ?: return
        ui.bike.text = ride.motorcycleName
        ui.date.text = getString(R.string.ride_saved_date, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ride.startedAt)))
        ui.metrics.text = "${rideDistance(ride.distanceMeters, ride.odometerUnit)}  ·  ${rideDuration(ride.durationMillis)}"
        ui.mileageStatus.text = when {
            ride.mileageDecision == Ride.APPLIED -> getString(R.string.ride_mileage_applied, ride.confirmedOdometer ?: 0, ride.odometerUnit)
            ride.mileageDecision == Ride.SKIPPED -> getString(R.string.ride_mileage_skipped)
            ride.motorcycleId == null -> getString(R.string.ride_bike_removed)
            else -> getString(R.string.ride_mileage_pending)
        }
        val pending = ride.mileageDecision == Ride.PENDING && ride.state == Ride.FINISHED
        ui.confirmMileage.isVisible = pending && ride.motorcycleId != null
        ui.skipMileage.isVisible = pending
        ui.confirmMileage.isEnabled = !model.busy.value
        ui.skipMileage.isEnabled = !model.busy.value
        ui.deleteRide.isEnabled = !model.busy.value
        val points = model.points.value
        val unavailable = getString(R.string.ride_unavailable)
        val altitude = points.mapNotNull { it.altitudeMeters }
        ui.statistics.text = getString(R.string.ride_stats_values,
            points.maxOfOrNull { it.speedMetersPerSecond }?.let { rideSpeed(it, ride.odometerUnit) } ?: unavailable,
            points.mapNotNull { it.acceleration }.maxOrNull()?.let { String.format(Locale.getDefault(), "%.1f m/s²", it) } ?: unavailable,
            points.mapNotNull { it.leanDegrees }.maxOrNull()?.let { "$it°" } ?: unavailable,
            if (altitude.isEmpty()) unavailable else String.format(Locale.getDefault(), "%.0f–%.0f m", altitude.min(), altitude.max()))
    }

    private fun confirmMileage() {
        if (mileageDialog?.isShowing == true) return
        viewLifecycleOwner.lifecycleScope.launch {
            val ride = model.ride.value ?: return@launch
            val current = model.currentOdometer() ?: return@launch
            val suggested = runCatching { Mileage.suggestedOdometer(current, ride.distanceMeters, ride.odometerUnit) }.getOrDefault(current)
            val field = TextInputEditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(suggested.toString())
                selectAll()
            }
            val container = TextInputLayout(requireContext()).apply {
                hint = getString(R.string.ride_odometer, ride.odometerUnit)
                val padding = (24 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding / 2, padding, 0)
                addView(field)
            }
            val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ride_confirm_mileage)
                .setMessage(getString(R.string.ride_mileage_prompt, suggested, ride.odometerUnit)).setView(container)
                .setNegativeButton(R.string.back, null).setPositiveButton(R.string.ride_confirm, null).create()
            mileageDialog = dialog
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = field.text?.toString()?.toIntOrNull()
                    if (value == null || value < current) container.error = getString(R.string.ride_invalid_odometer)
                    else { model.confirmMileage(current, value); dialog.dismiss() }
                }
            }
            dialog.show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("metric", metric.ordinal); binding?.routeMap?.saveState(outState); super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { mileageDialog?.dismiss(); mileageDialog = null; binding = null; super.onDestroyView() }
}
