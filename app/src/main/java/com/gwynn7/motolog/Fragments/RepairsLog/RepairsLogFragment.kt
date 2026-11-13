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
import androidx.navigation.navGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.RepairslogListBinding
import com.gwynn7.motolog.stop

class RepairsLogFragment : Fragment(), MenuProvider {
    private var _binding: RepairslogListBinding? = null
    private val binding get() = _binding!!
    private val motorcycleViewModel: MotorcycleViewModel by navGraphViewModels(R.id.bike_nav)
    private lateinit var adapter: RepairsLogAdapter
    private var filterIndex = 0
    private var oldestFirst = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = RepairslogListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bikeId = motorcycleViewModel.selectedBikeId ?: run {
            stop(activity)
            return
        }

        filterIndex = savedInstanceState?.getInt("filterIndex") ?: filterIndex
        oldestFirst = savedInstanceState?.getBoolean("oldestFirst") ?: oldestFirst
        adapter = RepairsLogAdapter(onLogClicked = { log ->
            findNavController().navigate(RepairsLogFragmentDirections.repairslistToRepairsadd(bikeId, log.typeIndex, log.id))
        }, onEmptyChanged = { empty ->
            _binding?.placeholder?.apply {
                visibility = if (empty) View.VISIBLE else View.GONE
                setText(if (filterIndex == 0) R.string.add_first_repair else R.string.log_no_matching_entries)
            }
        })
        adapter.filter(filterIndex - 1, oldestFirst)
        binding.rwRepairs.layoutManager = LinearLayoutManager(requireContext())
        binding.rwRepairs.adapter = adapter
        binding.fabAddRepairsLog.setOnClickListener {
            findNavController().navigate(RepairsLogFragmentDirections.repairslistToRepairsadd(bikeId))
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        motorcycleViewModel.getMotorcycle(bikeId).observe(viewLifecycleOwner) { bike ->
            if (bike == null) stop(activity)
        }
        motorcycleViewModel.getRepairsLogs(bikeId).observe(viewLifecycleOwner) { logs ->
            adapter.submitLogs(logs)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.filter_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.filter_menu) return false
        val filterList = resources.getStringArray(R.array.repair_types).toMutableList().apply {
            add(0, getString(R.string.all))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setSingleChoiceItems(filterList.toTypedArray(), filterIndex) { _, which -> filterIndex = which }
            .setTitle(R.string.filter_type)
            .setNegativeButton(R.string.from_old) { _, _ -> oldestFirst = true; adapter.filter(filterIndex - 1, oldestFirst) }
            .setPositiveButton(R.string.from_new) { _, _ -> oldestFirst = false; adapter.filter(filterIndex - 1, oldestFirst) }
            .show()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("filterIndex", filterIndex)
        outState.putBoolean("oldestFirst", oldestFirst)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.rwRepairs.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
    }
}
