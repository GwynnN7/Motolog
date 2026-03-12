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
import java.util.Calendar
import java.util.Date

class RepairsLogAddFragment : Fragment(), MenuProvider {
    private var _binding: RepairslogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<RepairsLogAddFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentPath: Path = Path.Add

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RepairslogAddBinding.inflate(inflater, container, false)


        binding.btDeleteRepair.setOnClickListener {
            deleteLog()
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (args.logIndex != -1) currentPath = Path.Edit

        binding.textViewRepairDistance.text = capitalize(
            getString(R.string.bike_repair_distance, UnitHelper.getDistanceText(requireContext()))
        )

        binding.etRepairDistance.setText(
            String.format("%d", args.currentBike.personal_km + args.currentBike.start_km)
        )

        if (currentPath == Path.Edit) {
            val currentLog = args.currentBike.logs.maintenance[args.logIndex]
            savedDate = currentLog.date

            binding.etRepairType.setText(currentLog.typeText)
            binding.etRepairNotes.setText(currentLog.notes)
            binding.etRepairPrice.setText(currentLog.price.toString())
            binding.etRepairDistance.setText(currentLog.repair_km.toString())
        }

        binding.ivRepairImage.setColorFilter(
            ContextCompat.getColor(requireContext(), repairColors[args.repairIndex])
        )

        binding.dpRepairDate.maxDate = Date().time
        binding.dpRepairDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth ->
            savedDate = longFromDate(year, month, dayOfMonth)
        }

        binding.btDeleteRepair.visibility = if (currentPath == Path.Edit) View.VISIBLE else View.GONE
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.save_menu) {
            insertDataToDatabase()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun insertDataToDatabase() {
        val type = binding.etRepairType.text.toString().trim()
        val notes = binding.etRepairNotes.text.toString().trim()
        val price = binding.etRepairPrice.text.toString()
        val distance = binding.etRepairDistance.text.toString()

        if (inputCheck(type, notes, price, distance)) {
            val bike = args.currentBike
            val repairsLogList = bike.logs.maintenance.toMutableList()
            val distanceInt = distance.toInt()

            val alert = MaterialAlertDialogBuilder(requireContext())
                .setPositiveButton(getString(R.string.ok), null)
                .setTitle(getString(R.string.repairlog_nomatch))

            if (dateFromLong(savedDate, Calendar.YEAR) < bike.year) {
                alert.setMessage(getString(R.string.log_nomatch_date)).show()
                return
            }

            if (distanceInt < bike.start_km) {
                alert.setMessage(getString(R.string.log_nomatch_distance)).show()
                return
            }

            if (currentPath == Path.Edit) repairsLogList.removeAt(args.logIndex)
            repairsLogList.add(RepairsLog(args.repairIndex, type, notes, savedDate, distanceInt, price.toDouble()))

            bike.logs.maintenance = repairsLogList.sortedBy { log -> log.date }.reversed()
            mMotorcycleViewModel.updateMotorcycle(bike, null)

            showToast(requireContext(), getString(R.string.log_saved))
            findNavController().navigateUp()
        } else showToast(requireContext(), getString(R.string.fill_fields))
    }

    private fun inputCheck(type: String, notes: String, price: String, distance: String): Boolean {
        return type.isNotEmpty() && notes.isNotEmpty() && price.isNotEmpty() && distance.isNotEmpty()
    }

    private fun deleteLog() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val bike = args.currentBike
                val repairsLogList = bike.logs.maintenance.toMutableList()
                repairsLogList.removeAt(args.logIndex)
                bike.logs.maintenance = repairsLogList.sortedBy { log -> log.date }.reversed()
                mMotorcycleViewModel.updateMotorcycle(bike, null)
                showToast(requireContext(), getString(R.string.log_removed))
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle(getString(R.string.title_question_remove_log))
            .setMessage(getString(R.string.description_question_remove_log))
            .show()
    }
}
