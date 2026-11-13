package com.gwynn7.motolog.Fragments.Garage

import android.os.Bundle
import java.util.Locale
import com.gwynn7.motolog.saveChanges
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.MainActivity
import com.gwynn7.motolog.photos.PhotoLoader
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.BikeHomeBinding
import com.gwynn7.motolog.formatThousand
import com.gwynn7.motolog.showToast
import com.gwynn7.motolog.stop

class BikeHomeFragment : Fragment(), MenuProvider {
    private var _binding: BikeHomeBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by navGraphViewModels(R.id.bike_nav)
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

        binding.editBike.setOnClickListener { editBike() }
        binding.rideBike.setOnClickListener { (requireActivity() as MainActivity).openRecorder() }

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

                binding.bikeAlias.text = currentBike.alias.ifEmpty { currentBike.model }

                binding.bikeDetails.text = listOfNotNull(
                    currentBike.manufacturer.takeIf { it.isNotBlank() },
                    currentBike.model.takeIf { currentBike.alias.isNotBlank() && it.isNotBlank() },
                    String.format(Locale.getDefault(), "%d", currentBike.year)
                ).joinToString(" · ")

                binding.bikePersonaldistance.text = "${formatThousand(currentBike.personal_km)} ${UnitHelper.getDistance()}"
                binding.bikeTotaldistance.text = "${formatThousand(currentBike.personal_km + currentBike.start_km)} ${UnitHelper.getDistance()}"


                binding.bikeImage.maxHeight = ((if (currentBike.image == null) 220 else 420) * resources.displayMetrics.density).toInt()
                binding.bikeImage.scaleType = ImageView.ScaleType.FIT_CENTER
                PhotoLoader.load(binding.bikeImage, currentBike.image, R.drawable.bike_home)

                binding.bikeHome.visibility = VISIBLE
            } else stop(activity)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.show_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (!::currentBike.isInitialized) return false
        when (menuItem.itemId) {
            R.id.edit_show_menu -> {
                editBike()
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

    private fun editBike() {
        if (::currentBike.isInitialized) findNavController().navigate(BikeHomeFragmentDirections.bikehomeToBikeedit(currentBike))
    }

    private fun deleteMotorcycle() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                saveChanges(mMotorcycleViewModel.deleteMotorcycle(currentBike)) {
                    showToast(requireContext(), getString(R.string.bike_delete))
                    stop(activity)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${currentBike.manufacturer} ${currentBike.model}?")
            .setMessage(getString(R.string.delete_bike_question))
            .show()
    }
}
