package com.gwynn7.motolog.Fragments.RepairsLog

import android.os.Bundle
import android.app.DatePickerDialog
import android.widget.ArrayAdapter
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.saveChanges
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.RepairslogAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
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
    private var draft: Bundle? = null
    private var dateDialog: DatePickerDialog? = null

    private val path get() = if (args.logId == NO_LOG_ID) Path.Add else Path.Edit
    private var repairType = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = RepairslogAddBinding.inflate(inflater, container, false)
        formBound = false
        draft = savedInstanceState?.getBundle("draft") ?: draft
        binding.saveLog.setOnClickListener { insertDataToDatabase() }
        binding.logDate.setOnClickListener { chooseDate() }
        binding.costInput.prefixText = UnitHelper.getCurrency() + " "
        binding.editorHeading.setText(if (path == Path.Edit) R.string.edit_log else R.string.add_log)
        val categories = resources.getStringArray(R.array.repair_types)
        binding.category.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        )
        binding.category.setOnItemClickListener { _, _, position, _ ->
            repairType = position
        }
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
        savedDate = draft?.getLong("date") ?: log?.date ?: savedDate
        repairType = (draft?.getInt("category") ?: log?.typeIndex ?: args.repairIndex).coerceIn(0, resources.getStringArray(R.array.repair_types).lastIndex)
        binding.category.setText(resources.getStringArray(R.array.repair_types)[repairType], false)
        binding.odometerInput.hint = getString(R.string.ride_odometer, UnitHelper.getDistance())
        binding.etRepairDistance.setText(draft?.getString("distance") ?: ((log?.repair_km
            ?: (bike.start_km + bike.personal_km))).toString())
        binding.etRepairType.setText(draft?.getString("title") ?: log?.typeText.orEmpty())
        binding.etRepairNotes.setText(draft?.getString("notes") ?: log?.notes.orEmpty())
        binding.etRepairPrice.setText(draft?.getString("price") ?: log?.price?.toString().orEmpty())
        binding.logDate.text = getString(R.string.log_date_value, longToDateString(savedDate))
        binding.btDeleteRepair.visibility = if (path == Path.Edit) View.VISIBLE else View.GONE
    }

    private fun insertDataToDatabase() {
        val bike = currentBike ?: return
        val type = binding.etRepairType.text.toString().trim()
        val notes = binding.etRepairNotes.text.toString().trim()
        val price = binding.etRepairPrice.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        val distance = binding.etRepairDistance.text.toString().trim().toIntOrNull()
        binding.etRepairType.error = if (type.isEmpty()) getString(R.string.log_title_required) else null
        binding.etRepairPrice.error = if (price == null || !price.isFinite() || price < 0) getString(R.string.log_invalid_cost) else null
        binding.etRepairDistance.error = if (distance == null || distance < 0) getString(R.string.ride_invalid_odometer) else null
        if (type.isEmpty() || price == null || !price.isFinite() || price < 0 || distance == null || distance < 0) return

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

        saveChanges(motorcycleViewModel.saveRepairsLog(
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
        )) {
            showToast(requireContext(), getString(R.string.log_saved))
            findNavController().navigateUp()
        }
    }

    private fun deleteLog() {
        val log = currentLog ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                saveChanges(motorcycleViewModel.deleteRepairsLog(log)) {
                    showToast(requireContext(), getString(R.string.log_removed))
                    findNavController().popBackStack(R.id.repairs_log, false)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle(getString(R.string.title_question_remove_log))
            .setMessage(getString(R.string.description_question_remove_log))
            .show()
    }

    private fun chooseDate() {
        dateDialog?.dismiss()
        dateDialog = DatePickerDialog(requireContext(), { _, year, month, day ->
            savedDate = longFromDate(year, month, day)
            binding.logDate.text = getString(R.string.log_date_value, longToDateString(savedDate))
        }, dateFromLong(savedDate, Calendar.YEAR), dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)).apply { datePicker.maxDate = Date().time; show() }
    }

    private fun captureDraft() = Bundle().apply {
        putLong("date", savedDate)
        putString("title", binding.etRepairType.text?.toString())
        putString("notes", binding.etRepairNotes.text?.toString())
        putString("price", binding.etRepairPrice.text?.toString())
        putString("distance", binding.etRepairDistance.text?.toString())
        putInt("category", repairType)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (formBound && _binding != null) draft = captureDraft()
        outState.putBundle("draft", draft)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        dateDialog?.dismiss(); dateDialog = null
        if (formBound) draft = captureDraft()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val NO_LOG_ID = -1
    }
}
