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
import com.gwynn7.motolog.databinding.InfoEngineinfoEditBinding
import com.gwynn7.motolog.showToast

class EditEngineFragment : Fragment(), MenuProvider {
    private var _binding: InfoEngineinfoEditBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<EditEngineFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoEngineinfoEditBinding.inflate(inflater, container, false)

        val bike = args.currentBike
        binding.etBikeCc.setText(bike.info.engine_cc.toString())
        binding.etBikeHp.setText(bike.info.horse_power.toString())
        binding.etBikeCylinders.setText(bike.info.cylinders.toString())
        binding.etBikeTorque.setText(bike.info.torque.toString())

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.save_menu) {
            val bike = args.currentBike
            bike.info.cylinders = if (binding.etBikeCylinders.text.isNotEmpty()) binding.etBikeCylinders.text.toString().toInt() else 0
            bike.info.engine_cc = if (binding.etBikeCc.text.isNotEmpty()) binding.etBikeCc.text.toString().toDouble() else 0.0
            bike.info.torque = if (binding.etBikeTorque.text.isNotEmpty()) binding.etBikeTorque.text.toString().toDouble() else 0.0
            bike.info.horse_power = if (binding.etBikeHp.text.isNotEmpty()) binding.etBikeHp.text.toString().toDouble() else 0.0
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
