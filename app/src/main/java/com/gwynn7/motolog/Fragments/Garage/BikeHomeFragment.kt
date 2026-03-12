package com.gwynn7.motolog.Fragments.Garage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.net.toFile
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
import com.gwynn7.motolog.capitalize
import com.gwynn7.motolog.databinding.BikeHomeBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.showToast
import com.gwynn7.motolog.stop

class BikeHomeFragment : Fragment(), MenuProvider {
    private var _binding: BikeHomeBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private lateinit var currentBike: Motorcycle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BikeHomeBinding.inflate(inflater, container, false)

        binding.distanceButton.setOnClickListener {
            findNavController().navigate(R.id.bikehome_to_bikedistance)
        }
        binding.infoButton.setOnClickListener {
            findNavController().navigate(R.id.bikehome_to_bikeinfo)
        }
        binding.modsButton.setOnClickListener {
            findNavController().navigate(R.id.bikehome_to_bikemods)
        }
        binding.repairButton.setOnClickListener {
            findNavController().navigate(R.id.bikehome_to_bikerepairs)
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

                if (currentBike.image != null && !currentBike.image!!.toFile().exists()) {
                    mMotorcycleViewModel.updateMotorcycle(currentBike, null, true)
                }

                binding.bikeAlias.text = currentBike.alias.ifEmpty { currentBike.model }
                binding.bikeAlias.isSelected = true

                binding.bikeManufacturer.text = currentBike.manufacturer
                binding.bikeModel.text = currentBike.model
                binding.bikeYear.text = String.format("%d", currentBike.year)

                binding.bikePersonaldistance.text = formatThousand(currentBike.personal_km)
                binding.bikeTotaldistance.text = formatThousand(currentBike.personal_km + currentBike.start_km)

                binding.totalBikeDistance.text = capitalize(getString(R.string.total_bike_distance, UnitHelper.getDistanceText(requireContext())))
                binding.personalBikeDistance.text = capitalize(getString(R.string.personal_bike_distance, UnitHelper.getDistanceText(requireContext())))

                if (currentBike.image != null) binding.bikeImage.setImageURI(currentBike.image)
                else {
                    binding.bikeImage.setImageResource(R.drawable.bike_home)
                    binding.cvBikeImage.radius = 0F
                    binding.cvBikeImage.scaleX = 1.3F
                    binding.cvBikeImage.scaleY = 1.3F
                    binding.bikeImage.scaleX = 1.1F
                    binding.bikeImage.scaleY = 1.1F
                    binding.bikeImage.setPadding(0, 60, 0, 0)
                }

                binding.bikeHome.visibility = VISIBLE
            } else stop(activity)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.show_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.edit_show_menu -> {
                val action = BikeHomeFragmentDirections.bikehomeToBikeedit(currentBike)
                findNavController().navigate(action)
            }
            R.id.delete_show_menu -> deleteMotorcycle()
            else -> return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun deleteMotorcycle() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                mMotorcycleViewModel.deleteMotorcycle(currentBike)
                showToast(requireContext(), getString(R.string.bike_delete))
                stop(activity)
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${currentBike.manufacturer} ${currentBike.model}?")
            .setMessage(getString(R.string.delete_bike_question))
            .show()
    }
}