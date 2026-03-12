package com.gwynn7.motolog.Fragments.Garage

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.DistanceDialogBinding
import com.gwynn7.motolog.databinding.MotorcycleListBinding
import com.gwynn7.motolog.formatThousand

class MotorcycleListFragment : Fragment(), MenuProvider {
    private var _binding: MotorcycleListBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MotorcycleListBinding.inflate(inflater, container, false)

        binding.fabAddMotorcycle.setOnClickListener {
            val action = MotorcycleListFragmentDirections.bikelistToBikeadd(null)
            findNavController().navigate(action)
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val adapter = MotorcycleListAdapter()
        binding.rwMotorcycles.adapter = adapter
        binding.rwMotorcycles.layoutManager = LinearLayoutManager(requireContext())

        mMotorcycleViewModel.readAllData.observe(viewLifecycleOwner) { motorcycles ->
            binding.placeholder.visibility = if (motorcycles.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(motorcycles.reversed())
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.distance_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.distance_menu) {
            val dialogBinding = DistanceDialogBinding.inflate(layoutInflater)

            var totalDistance = 0
            val bikesList = mMotorcycleViewModel.readAllData.value!!
            for (bike in bikesList) totalDistance += bike.personal_km

            dialogBinding.distance.text = String.format(
                "%s %s", formatThousand(totalDistance), UnitHelper.getDistance()
            )

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
