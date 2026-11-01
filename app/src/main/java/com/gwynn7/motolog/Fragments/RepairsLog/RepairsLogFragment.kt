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
import androidx.fragment.app.activityViewModels
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
    private val motorcycleViewModel: MotorcycleViewModel by activityViewModels()
    private lateinit var adapter: RepairsLogAdapter
    private var filterIndex = 0

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

        adapter = RepairsLogAdapter { log -> chooseRepairType(bikeId, log.id, log.typeIndex) }
        binding.rwRepairs.layoutManager = LinearLayoutManager(requireContext())
        binding.rwRepairs.adapter = adapter
        binding.fabAddRepairsLog.setOnClickListener { chooseRepairType(bikeId) }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        motorcycleViewModel.getMotorcycle(bikeId).observe(viewLifecycleOwner) { bike ->
            if (bike == null) stop(activity)
        }
        motorcycleViewModel.getRepairsLogs(bikeId).observe(viewLifecycleOwner) { logs ->
            binding.placeholder.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
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
            .setNegativeButton(R.string.from_old) { _, _ -> adapter.filter(filterIndex - 1, oldestFirst = true) }
            .setPositiveButton(R.string.from_new) { _, _ -> adapter.filter(filterIndex - 1, oldestFirst = false) }
            .show()
        return true
    }

    private fun chooseRepairType(bikeId: Int, logId: Int = NO_LOG_ID, selectedType: Int = 0) {
        var repairType = selectedType
        MaterialAlertDialogBuilder(requireContext())
            .setNegativeButton(R.string.back, null)
            .setSingleChoiceItems(resources.getStringArray(R.array.repair_types), selectedType) { _, which ->
                repairType = which
            }
            .setTitle(R.string.choose_repair_type)
            .setPositiveButton(if (logId == NO_LOG_ID) R.string.add_log else R.string.edit) { _, _ ->
                findNavController().navigate(
                    RepairsLogFragmentDirections.repairslistToRepairsadd(bikeId, repairType, logId)
                )
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val NO_LOG_ID = -1
    }
}
