package com.gwynn7.motolog.Fragments.RepairsLog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.capitalize
import com.gwynn7.motolog.databinding.RepairslogAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.repairColors
import com.gwynn7.motolog.showToast
import com.gwynn7.motolog.stop
import java.util.Calendar
import java.util.Date

class RepairsLogAddFragment : Fragment(), MenuProvider {
    private var _binding: RepairslogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<RepairsLogAddFragmentArgs>()
    private val motorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentBike: Motorcycle? = null
    private var currentLog: RepairsLog? = null
    private var formBound = false

    private val path get() = if (args.logId == NO_LOG_ID) Path.Add else Path.Edit
    private val repairType get() = args.repairIndex.coerceIn(repairColors.indices)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = RepairslogAddBinding.inflate(inflater, container, false)
        binding.btDeleteRepair.setOnClickListener { deleteLog() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        motorcycleViewModel.getMotorcycle(args.bikeId).observe(viewLifecycleOwner) { bike ->
            currentBike = bike
            if (bike == null) stop(activity) else bindFormWhenReady()
        }
        motorcycleViewModel.getRepairsLogs(args.bikeId).observe(viewLifecycleOwner) { logs ->
            if (path == Path.Edit) currentLog = logs.firstOrNull { it.id == args.logId }
            if (path == Path.Edit && currentLog == null) {
                findNavController().navigateUp()
                return@observe
            }
            bindFormWhenReady()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.save_menu -> {
            insertDataToDatabase()
            true
        }
        else -> false
    }

    private fun bindFormWhenReady() {
        if (formBound) return
        val bike = currentBike ?: return
        val log = if (path == Path.Edit) currentLog ?: return else null
        formBound = true
        savedDate = log?.date ?: savedDate
        binding.textViewRepairDistance.text = capitalize(
            getString(R.string.bike_repair_distance, UnitHelper.getDistanceText(requireContext()))
        )
        binding.etRepairDistance.setText((log?.repair_km ?: bike.start_km + bike.personal_km).toString())
        binding.etRepairType.setText(log?.typeText.orEmpty())
        binding.etRepairNotes.setText(log?.notes.orEmpty())
        binding.etRepairPrice.setText(log?.price?.toString().orEmpty())
        binding.ivRepairImage.setColorFilter(ContextCompat.getColor(requireContext(), repairColors[repairType]))
        binding.dpRepairDate.maxDate = Date().time
        binding.dpRepairDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth -> savedDate = longFromDate(year, month, dayOfMonth) }
        binding.btDeleteRepair.visibility = if (path == Path.Edit) View.VISIBLE else View.GONE
    }

    private fun insertDataToDatabase() {
        val bike = currentBike ?: return
        val type = binding.etRepairType.text.toString().trim()
        val notes = binding.etRepairNotes.text.toString().trim()
        val price = binding.etRepairPrice.text.toString().trim().toDoubleOrNull()
        val distance = binding.etRepairDistance.text.toString().trim().toIntOrNull()
        if (type.isEmpty() || notes.isEmpty() || price == null || !price.isFinite() || price < 0 ||
            distance == null || distance < 0
        ) {
            showToast(requireContext(), getString(R.string.fill_fields))
            return
        }

        val alert = MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.ok), null)
            .setTitle(getString(R.string.repairlog_nomatch))
        when {
            dateFromLong(savedDate, Calendar.YEAR) < bike.year -> {
                alert.setMessage(getString(R.string.log_nomatch_date)).show()
                return
            }
            distance < bike.start_km -> {
                alert.setMessage(getString(R.string.log_nomatch_distance)).show()
                return
            }
        }

        motorcycleViewModel.saveRepairsLog(
            RepairsLog(
                id = currentLog?.id ?: 0,
                motorcycleId = bike.id,
                typeIndex = repairType,
                typeText = type,
                notes = notes,
                date = savedDate,
                repair_km = distance,
                price = price
            )
        )
        showToast(requireContext(), getString(R.string.log_saved))
        findNavController().navigateUp()
    }

    private fun deleteLog() {
        val log = currentLog ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                motorcycleViewModel.deleteRepairsLog(log)
                showToast(requireContext(), getString(R.string.log_removed))
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle(getString(R.string.title_question_remove_log))
            .setMessage(getString(R.string.description_question_remove_log))
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
