package com.gwynn7.motolog.Fragments.ModsLog

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.ModslogAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.showToast
import com.gwynn7.motolog.stop
import java.util.Calendar
import java.util.Date

class ModsLogAddFragment : Fragment(), MenuProvider {
    private var _binding: ModslogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ModsLogAddFragmentArgs>()
    private val motorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentLog: ModsLog? = null
    private var bikeExists = false
    private var formBound = false

    private val path get() = if (args.logId == NO_LOG_ID) Path.Add else Path.Edit

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ModslogAddBinding.inflate(inflater, container, false)
        binding.btDeleteMod.setOnClickListener { deleteLog() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        motorcycleViewModel.getMotorcycle(args.bikeId).observe(viewLifecycleOwner) { bike ->
            bikeExists = bike != null
            if (!bikeExists) stop(activity) else bindFormWhenReady()
        }
        motorcycleViewModel.getModsLogs(args.bikeId).observe(viewLifecycleOwner) { logs ->
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
        if (formBound || !bikeExists) return
        val log = if (path == Path.Edit) currentLog ?: return else null
        formBound = true
        savedDate = log?.date ?: savedDate
        binding.etModTitle.setText(log?.title.orEmpty())
        binding.etModTitle.isSelected = true
        binding.etModDescription.setText(log?.description.orEmpty())
        binding.etModPrice.setText(log?.price?.toString().orEmpty())
        binding.dpModDate.maxDate = Date().time
        binding.dpModDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth -> savedDate = longFromDate(year, month, dayOfMonth) }
        binding.btDeleteMod.visibility = if (path == Path.Edit) View.VISIBLE else View.GONE
    }

    private fun insertDataToDatabase() {
        val title = binding.etModTitle.text.toString().trim()
        val description = binding.etModDescription.text.toString().trim()
        val price = binding.etModPrice.text.toString().trim().toDoubleOrNull()
        if (title.isEmpty() || description.isEmpty() || price == null || price < 0 || !price.isFinite()) {
            showToast(requireContext(), getString(R.string.fill_fields))
            return
        }

        motorcycleViewModel.saveModsLog(
            ModsLog(
                id = currentLog?.id ?: 0,
                motorcycleId = args.bikeId,
                title = title,
                description = description,
                date = savedDate,
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
                motorcycleViewModel.deleteModsLog(log)
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
