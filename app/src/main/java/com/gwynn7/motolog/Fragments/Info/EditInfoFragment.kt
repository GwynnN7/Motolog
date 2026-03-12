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
import androidx.navigation.fragment.navArgs
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.InfoBikeinfoEditBinding
import com.gwynn7.motolog.showToast

class EditInfoFragment : Fragment(), MenuProvider {
    private var _binding: InfoBikeinfoEditBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<EditInfoFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBikeinfoEditBinding.inflate(inflater, container, false)

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val bike = args.currentBike
        binding.etBikePrice.setText(bike.info.price.toString())
        binding.etBikeLicensePlate.setText(bike.info.plate_number)
        binding.etBikeFrontTire.setText(bike.info.front_tire)
        binding.etBikeRearTire.setText(bike.info.rear_tire)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.save_menu) {
            val bike = args.currentBike
            bike.info.front_tire = binding.etBikeFrontTire.text.toString()
            bike.info.rear_tire = binding.etBikeRearTire.text.toString()
            bike.info.plate_number = String.format("%S", binding.etBikeLicensePlate.text.toString())
            bike.info.price = if (binding.etBikePrice.text.isNotEmpty()) binding.etBikePrice.text.toString().toDouble() else 0.0
            mMotorcycleViewModel.updateMotorcycle(bike, null)
            showToast(requireContext(), getString(R.string.info_saved))
            findNavController().navigateUp()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
