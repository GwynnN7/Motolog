package com.gwynn7.motolog.Fragments.Gear

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
import com.gwynn7.motolog.ViewModel.GearViewModel
import com.gwynn7.motolog.databinding.GearCostDialogBinding
import com.gwynn7.motolog.databinding.GearListBinding

class GearListFragment : Fragment(), MenuProvider {
    private var _binding: GearListBinding? = null
    private val binding get() = _binding!!
    private val mGearViewModel: GearViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GearListBinding.inflate(inflater, container, false)

        binding.fabAddGear.setOnClickListener {
            val action = GearListFragmentDirections.gearlistToGearadd(null)
            findNavController().navigate(action)
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val adapter = GearListAdapter()
        binding.rwGears.adapter = adapter
        binding.rwGears.layoutManager = LinearLayoutManager(requireContext())

        mGearViewModel.readAllData.observe(viewLifecycleOwner) { gears ->
            binding.placeholder.visibility = if (gears.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(gears.sortedBy { gear -> gear.date }.reversed())
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.money_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.money_menu) {
            val dialogBinding = GearCostDialogBinding.inflate(layoutInflater)

            var totalMoney = 0.0
            val gearList = mGearViewModel.readAllData.value!!
            for (gear in gearList) totalMoney += gear.price

            dialogBinding.cost.text = String.format("%.2f%s", totalMoney, UnitHelper.getCurrency())

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
