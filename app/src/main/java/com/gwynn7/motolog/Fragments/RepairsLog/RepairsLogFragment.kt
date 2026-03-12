package com.gwynn7.motolog.Fragments.RepairsLog

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
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.RepairslogListBinding
import com.gwynn7.motolog.stop

class RepairsLogFragment : Fragment(), MenuProvider {
    private var _binding: RepairslogListBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private lateinit var currentBike: Motorcycle
    private lateinit var adapter: RepairsLogAdapter
    var filterIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RepairslogListBinding.inflate(inflater, container, false)

        var arrayType = 0
        binding.fabAddRepairsLog.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setNegativeButton(R.string.back, null)
                .setSingleChoiceItems(resources.getStringArray(R.array.repair_types), 0) { _, which ->
                    arrayType = which
                }
                .setTitle(R.string.choose_repair_type)
                .setPositiveButton(R.string.add_log) { _, _ ->
                    val action = RepairsLogFragmentDirections.repairslistToRepairsadd(currentBike, arrayType)
                    findNavController().navigate(action)
                }
                .show()
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        adapter = RepairsLogAdapter()
        binding.rwRepairs.adapter = adapter
        binding.rwRepairs.layoutManager = LinearLayoutManager(requireContext())

        val bikeId = MotorcycleViewModel.currentBikeId
        if (bikeId == null) {
            stop(activity)
            return
        }

        val bikeData = mMotorcycleViewModel.getMotorcycle(bikeId)
        bikeData.observe(viewLifecycleOwner) { bikes ->
            if (bikes.isNotEmpty()) {
                currentBike = bikes.first()
                binding.placeholder.visibility = if (currentBike.logs.maintenance.isEmpty()) View.VISIBLE else View.GONE
                adapter.bindBike(currentBike)
            } else stop(activity)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.filter_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.filter_menu) {
            val filterList = resources.getStringArray(R.array.repair_types).toMutableList()
            filterList.add(0, resources.getString(R.string.all))
            MaterialAlertDialogBuilder(requireContext())
                .setSingleChoiceItems(filterList.toTypedArray(), filterIndex) { _, which ->
                    filterIndex = which
                }
                .setTitle(R.string.filter_type)
                .setNegativeButton(R.string.from_old) { _, _ ->
                    adapter.filter(filterIndex - 1, true)
                }
                .setPositiveButton(R.string.from_new) { _, _ ->
                    adapter.filter(filterIndex - 1, false)
                }
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
