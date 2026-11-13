package com.gwynn7.motolog.Fragments.DistanceLog

import android.os.Bundle
import android.app.DatePickerDialog
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
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.databinding.DistancelogAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.showToast
import com.gwynn7.motolog.stop
import java.util.Calendar
import java.util.Date

class DistanceLogAddFragment : Fragment(), MenuProvider {
    private var _binding: DistancelogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<DistanceLogAddFragmentArgs>()
    private val motorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentBike: Motorcycle? = null
    private var currentLog: DistanceLog? = null
    private var distanceLogs: List<DistanceLog> = emptyList()
    private var formBound = false
    private var logsLoaded = false
    private var draft: Bundle? = null
    private var dateDialog: DatePickerDialog? = null

    private val path get() = if (args.logId == NO_LOG_ID) Path.Add else Path.Edit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DistancelogAddBinding.inflate(inflater, container, false)
        formBound = false
        logsLoaded = false
        draft = savedInstanceState?.getBundle("draft") ?: draft
        binding.editorHeading.setText(if (path == Path.Edit) R.string.edit_log else R.string.add_log)
        binding.odometerInput.hint = getString(R.string.ride_odometer, UnitHelper.getDistance())
        binding.saveLog.setOnClickListener { insertDataToDatabase() }
        binding.logDate.setOnClickListener { chooseDate() }
        binding.btDeleteDistanceLog.setOnClickListener { deleteLog() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        motorcycleViewModel.getMotorcycle(args.bikeId).observe(viewLifecycleOwner) { bike ->
            currentBike = bike
            if (bike == null) stop(activity) else bindFormWhenReady()
        }
        motorcycleViewModel.getDistanceLogs(args.bikeId).observe(viewLifecycleOwner) { logs ->
            logsLoaded = true
            distanceLogs = logs
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
        if (formBound || !logsLoaded) return
        val bike = currentBike ?: return
        val log = if (path == Path.Edit) currentLog ?: return else null
        formBound = true

        savedDate = draft?.getLong("date") ?: log?.date ?: savedDate
        binding.etDistancelog.setText(draft?.getString("distance") ?: ((log?.distance
            ?: (bike.start_km + bike.personal_km))).toString())
        binding.logDate.text = getString(R.string.log_date_value, longToDateString(savedDate))
        binding.btDeleteDistanceLog.visibility = if (path == Path.Edit) View.VISIBLE else View.GONE
    }

    private fun insertDataToDatabase() {
        if (!formBound || !logsLoaded) return
        val bike = currentBike ?: return
        val distance = binding.etDistancelog.text.toString().trim().toIntOrNull()
        binding.odometerInput.error = if (distance == null || distance < 0) getString(R.string.ride_invalid_odometer) else null
        if (distance == null || distance < 0) return

        val alert = MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.ok), null)
            .setTitle(getString(R.string.log_nomatch))
        when {
            dateFromLong(savedDate, Calendar.YEAR) < bike.year -> {
                alert.setMessage(getString(R.string.log_nomatch_date)).show()
                return
            }
            distance < bike.start_km -> {
                alert.setMessage(getString(R.string.log_nomatch_distance)).show()
                return
            }
            distanceLogs.any { it.id != currentLog?.id && inputCheck(it, distance) } -> {
                alert.setMessage(getString(R.string.log_nomatch_distancelogs)).show()
                return
            }
        }

        saveChanges(motorcycleViewModel.saveDistanceLog(
            DistanceLog(
                id = currentLog?.id ?: 0,
                motorcycleId = bike.id,
                distance = distance,
                date = savedDate
            )
        )) {
            showToast(requireContext(), getString(R.string.log_saved))
            findNavController().navigateUp()
        }
    }

    private fun inputCheck(log: DistanceLog, distance: Int): Boolean =
        (log.date < savedDate && log.distance > distance) ||
            (log.date > savedDate && log.distance < distance)

    private fun deleteLog() {
        val log = currentLog ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                saveChanges(motorcycleViewModel.deleteDistanceLog(log)) {
                    showToast(requireContext(), getString(R.string.log_removed))
                    findNavController().popBackStack(R.id.distance_log, false)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle(getString(R.string.title_question_remove_log))
            .setMessage(getString(R.string.description_question_remove_log))
            .show()
    }

    private fun chooseDate() {
        if (!formBound) return
        dateDialog?.dismiss()
        dateDialog = DatePickerDialog(requireContext(), { _, year, month, day ->
            savedDate = longFromDate(year, month, day)
            binding.logDate.text = getString(R.string.log_date_value, longToDateString(savedDate))
        }, dateFromLong(savedDate, Calendar.YEAR), dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)).apply { datePicker.maxDate = Date().time; show() }
    }

    private fun captureDraft() = Bundle().apply {
        putLong("date", savedDate)
        putString("distance", binding.etDistancelog.text?.toString())
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
