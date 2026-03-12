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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
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
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private lateinit var currentBike: Motorcycle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)

        binding.btEditEngine.setOnClickListener {
            val action = InfoFragmentDirections.infoToEditengine(currentBike)
            findNavController().navigate(action)
        }
        binding.btEditExpiry.setOnClickListener {
            val action = InfoFragmentDirections.infoToEditexpiry(currentBike)
            findNavController().navigate(action)
        }
        binding.btEditInfo.setOnClickListener {
            val action = InfoFragmentDirections.infoToEditinfo(currentBike)
            findNavController().navigate(action)
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val bikeId = MotorcycleViewModel.currentBikeId
        if (bikeId == null) {
            stop(activity)
            return
        }

        val bikeData = mMotorcycleViewModel.getMotorcycle(bikeId)
        bikeData.observe(viewLifecycleOwner) { bikes ->
            if (bikes.isNotEmpty()) {
                currentBike = bikes.first()

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
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.money_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.money_menu) {
            val dialogBinding = CostDialogBinding.inflate(layoutInflater)

            var totalMaintenanceMoney = 0.0
            val maintenanceList = currentBike.logs.maintenance
            for (repair in maintenanceList) totalMaintenanceMoney += repair.price

            var totalModsMoney = 0.0
            val modsList = currentBike.logs.mods
            for (mod in modsList) totalModsMoney += mod.price

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
