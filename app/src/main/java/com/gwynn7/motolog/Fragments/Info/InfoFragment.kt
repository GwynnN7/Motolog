package com.gwynn7.motolog.Fragments.Info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.CostDialogBinding
import com.gwynn7.motolog.databinding.InfoBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.stop
import java.util.Calendar

class InfoFragment : Fragment(), MenuProvider {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by activityViewModels()
    private lateinit var currentBike: Motorcycle
    private var repairsLogs: List<RepairsLog> = emptyList()
    private var modsLogs: List<ModsLog> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)

        binding.btEditEngine.setOnClickListener {
            if (!::currentBike.isInitialized) return@setOnClickListener
            val action = InfoFragmentDirections.infoToEditengine(currentBike)
            findNavController().navigate(action)
        }
        binding.btEditExpiry.setOnClickListener {
            if (!::currentBike.isInitialized) return@setOnClickListener
            val action = InfoFragmentDirections.infoToEditexpiry(currentBike)
            findNavController().navigate(action)
        }
        binding.btEditInfo.setOnClickListener {
            if (!::currentBike.isInitialized) return@setOnClickListener
            val action = InfoFragmentDirections.infoToEditinfo(currentBike)
            findNavController().navigate(action)
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bikeId = mMotorcycleViewModel.selectedBikeId
        if (bikeId == null) {
            stop(activity)
            return
        }

        val bikeData = mMotorcycleViewModel.getMotorcycle(bikeId)
        bikeData.observe(viewLifecycleOwner) { bike ->
            if (bike != null) {
                currentBike = bike

                binding.bikeCc.text = currentBike.info.engine_cc.toString()
                binding.bikeHp.text = currentBike.info.horse_power.toString()
                binding.bikeTorque.text = currentBike.info.torque.toString()
                binding.bikeCylinders.text = currentBike.info.cylinders.toString()

                binding.bikePrice.text = String.format("%.2f%s", currentBike.info.price, UnitHelper.getCurrency())
                binding.bikeLicensePlate.text = currentBike.info.plate_number.ifEmpty { getString(R.string.not_set) }
                binding.bikeFrontTire.text = currentBike.info.front_tire.ifEmpty { getString(R.string.not_set) }
                binding.bikeRearTire.text = currentBike.info.rear_tire.ifEmpty { getString(R.string.not_set) }

                val currentTime = Calendar.getInstance().timeInMillis
                binding.bikeInsurance.text = if (currentBike.expiry.insurance > currentTime) longToDateString(currentBike.expiry.insurance) else getString(R.string.not_updated)
                binding.bikeTax.text = if (currentBike.expiry.tax > currentTime) longToDateString(currentBike.expiry.tax) else getString(R.string.not_updated)
                binding.bikeInspection.text = if (currentBike.expiry.inspection > currentTime) longToDateString(currentBike.expiry.inspection) else getString(R.string.not_updated)
            } else stop(activity)
        }
        mMotorcycleViewModel.getRepairsLogs(bikeId).observe(viewLifecycleOwner) { repairsLogs = it }
        mMotorcycleViewModel.getModsLogs(bikeId).observe(viewLifecycleOwner) { modsLogs = it }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.money_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (!::currentBike.isInitialized) return false
        if (menuItem.itemId == R.id.money_menu) {
            val dialogBinding = CostDialogBinding.inflate(layoutInflater)

            val totalMaintenanceMoney = repairsLogs.sumOf { it.price }
            val totalModsMoney = modsLogs.sumOf { it.price }

            dialogBinding.maintenanceCost.text = String.format("%.2f%s", totalMaintenanceMoney, UnitHelper.getCurrency())
            dialogBinding.modsCost.text = String.format("%.2f%s", totalModsMoney, UnitHelper.getCurrency())
            dialogBinding.totalSpent.text = String.format("%.2f%s", totalMaintenanceMoney + totalModsMoney, UnitHelper.getCurrency())
            dialogBinding.totalSpentBike.text = String.format("%.2f%s", totalMaintenanceMoney + totalModsMoney + currentBike.info.price, UnitHelper.getCurrency())

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .show()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
