package com.gwynn7.motolog.Fragments.Info

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.InfoBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.stop
import com.gwynn7.motolog.ui.calendarDaysBetween
import com.gwynn7.motolog.ui.formatMoney
import com.gwynn7.motolog.ui.specification

class InfoFragment : Fragment(R.layout.info) {
    private var binding: InfoBinding? = null
    private val model: MotorcycleViewModel by navGraphViewModels(R.id.bike_nav)
    private var bike: Motorcycle? = null
    private var maintenanceCost = 0.0
    private var modsCost = 0.0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = InfoBinding.bind(view).also { binding = it }
        val id = model.selectedBikeId ?: run { stop(activity); return }
        ui.btEditTires.setOnClickListener { bike?.let {
            findNavController().navigate(R.id.edit_info, androidx.core.os.bundleOf("currentBike" to it, "editSection" to "tires"))
        } }
        ui.btEditEngine.setOnClickListener { bike?.let { findNavController().navigate(InfoFragmentDirections.infoToEditengine(it)) } }
        ui.btEditCost.setOnClickListener { bike?.let { findNavController().navigate(InfoFragmentDirections.infoToEditinfo(it)) } }
        ui.btEditInfo.setOnClickListener { bike?.let { findNavController().navigate(InfoFragmentDirections.infoToEditinfo(it)) } }
        ui.btEditExpiry.setOnClickListener { bike?.let { findNavController().navigate(InfoFragmentDirections.infoToEditexpiry(it)) } }
        model.getMotorcycle(id).observe(viewLifecycleOwner) {
            bike = it
            if (it == null) stop(activity) else render()
        }
        model.getRepairsLogs(id).observe(viewLifecycleOwner) { maintenanceCost = it.sumOf { log -> log.price }; render() }
        model.getModsLogs(id).observe(viewLifecycleOwner) { modsCost = it.sumOf { log -> log.price }; render() }
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val ui = binding ?: return
        val value = bike ?: return
        ui.bikeSummary.text = value.alias.ifBlank { "${value.manufacturer} ${value.model}" }
        ui.bikeCc.text = requireContext().specification(value.info.engine_cc)
        ui.bikeHp.text = requireContext().specification(value.info.horse_power)
        ui.bikeTorque.text = requireContext().specification(value.info.torque)
        ui.bikeCylinders.text = requireContext().specification(value.info.cylinders.toDouble())
        ui.bikePrice.text = formatMoney(value.info.price)
        ui.bikeLicensePlate.text = value.info.plate_number.ifBlank { getString(R.string.not_set) }
        ui.bikeFrontTire.text = value.info.front_tire.ifBlank { getString(R.string.not_set) }
        ui.bikeRearTire.text = value.info.rear_tire.ifBlank { getString(R.string.not_set) }
        deadline(ui.bikeInsurance, value.expiry.insurance)
        deadline(ui.bikeTax, value.expiry.tax)
        deadline(ui.bikeInspection, value.expiry.inspection)
        ui.maintenanceCost.text = formatMoney(maintenanceCost)
        ui.modsCost.text = formatMoney(modsCost)
        ui.totalSpent.text = formatMoney(maintenanceCost + modsCost)
        ui.totalSpentBike.text = formatMoney(maintenanceCost + modsCost + value.info.price)
    }

    private fun deadline(view: TextView, date: Long) {
        val days = if (date >= 0) calendarDaysBetween(System.currentTimeMillis(), date) else null
        val status = when {
            days == null -> getString(R.string.profile_no_date)
            days == 0L -> getString(R.string.profile_due_today)
            days < 0 -> getString(R.string.profile_overdue, resources.getQuantityString(R.plurals.interval_days, (-days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), -days))
            else -> getString(R.string.profile_due_in, resources.getQuantityString(R.plurals.interval_days, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), days))
        }
        view.text = if (days == null) status else "${longToDateString(date)}\n$status"
        val color = when {
            days == null -> com.google.android.material.R.attr.colorOnSurfaceVariant
            days < 0 -> com.google.android.material.R.attr.colorOnError
            days <= 30 -> com.google.android.material.R.attr.colorOnPrimary
            else -> com.google.android.material.R.attr.colorOnSurface
        }
        view.setTextColor(MaterialColors.getColor(view, color))
    }

    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
