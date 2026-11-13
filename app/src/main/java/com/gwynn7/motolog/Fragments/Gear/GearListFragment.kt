package com.gwynn7.motolog.Fragments.Gear

import android.os.Bundle
import java.util.Locale
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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

        val addProfile = View.OnClickListener {
            val action = GearListFragmentDirections.gearlistToGearadd(null)
            findNavController().navigate(action)
        }

        binding.fabAddGear.setOnClickListener(addProfile)
        binding.addFirstProfile.setOnClickListener(addProfile)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = GearListAdapter()
        binding.rwGears.adapter = adapter
        binding.rwGears.layoutManager = LinearLayoutManager(requireContext())

        mGearViewModel.readAllData.observe(viewLifecycleOwner) { gears ->
            val empty = gears.isEmpty()
            binding.emptyState.isVisible = empty
            binding.fabAddGear.isVisible = !empty
            binding.rwGears.isVisible = !empty
            adapter.submitList(gears.sortedBy { gear -> gear.date }.reversed())
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.money_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.money_menu) {
            val dialogBinding = GearCostDialogBinding.inflate(layoutInflater)

            val totalMoney = mGearViewModel.readAllData.value.orEmpty().sumOf { it.price }

            dialogBinding.cost.text = String.format(Locale.getDefault(), "%.2f%s", totalMoney, UnitHelper.getCurrency())

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .show()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        binding.rwGears.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
