package com.gwynn7.motolog.Fragments.DistanceLog

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
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.getUpdatedBikeDistance
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.capitalize
import com.gwynn7.motolog.databinding.DistancelogAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.showToast
import java.util.Calendar
import java.util.Date

class DistanceLogAddFragment : Fragment(), MenuProvider {
    private var _binding: DistancelogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<DistanceLogAddFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentPath: Path = Path.Add

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DistancelogAddBinding.inflate(inflater, container, false)

        binding.btDeleteDistanceLog.setOnClickListener {
            deleteLog()
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        if (args.logIndex != -1) currentPath = Path.Edit

        binding.textViewDistancelog.text = capitalize(
            getString(R.string.bike_distance_date, UnitHelper.getDistanceText(requireContext()))
        )

        if (currentPath == Path.Edit) {
            val currentLog = args.currentBike.logs.distance[args.logIndex]
            savedDate = currentLog.date
            binding.etDistancelog.setText(currentLog.distance.toString())
        } else {
            binding.etDistancelog.setText(
                String.format("%d", args.currentBike.start_km + args.currentBike.personal_km)
            )
        }

        binding.dpDistancelogDate.maxDate = Date().time
        binding.dpDistancelogDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth ->
            savedDate = longFromDate(year, month, dayOfMonth)
        }

        binding.btDeleteDistanceLog.visibility = if (currentPath == Path.Edit) View.VISIBLE else View.GONE
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

    private fun insertDataToDatabase() {
        val distance = binding.etDistancelog.text.toString()

        if (distance.isNotEmpty()) {
            val distanceInt = distance.toInt()
            val bike = args.currentBike
            val distanceLogList = bike.logs.distance.toMutableList()

            val alert = MaterialAlertDialogBuilder(requireContext())
                .setPositiveButton(getString(R.string.ok), null)
                .setTitle(getString(R.string.log_nomatch))

            if (dateFromLong(savedDate, Calendar.YEAR) < bike.year) {
                alert.setMessage(getString(R.string.log_nomatch_date)).show()
                return
            }

            if (distanceInt < bike.start_km) {
                alert.setMessage(getString(R.string.log_nomatch_distance)).show()
                return
            }

            for (log in distanceLogList) {
                if (args.logIndex == distanceLogList.indexOf(log)) continue
                if (inputCheck(log, distanceInt)) {
                    alert.setMessage(getString(R.string.log_nomatch_distancelogs)).show()
                    return
                }
            }

            if (currentPath == Path.Edit) distanceLogList.removeAt(args.logIndex)
            distanceLogList.add(DistanceLog(distanceInt, savedDate))

            bike.logs.distance = distanceLogList.sortedBy { log -> log.date }.reversed()
            bike.personal_km = getUpdatedBikeDistance(bike)
            mMotorcycleViewModel.updateMotorcycle(bike, null)

            showToast(requireContext(), getString(R.string.log_saved))
            findNavController().navigateUp()
        } else showToast(requireContext(), getString(R.string.fill_fields))
    }

    private fun inputCheck(log: DistanceLog, distance: Int): Boolean {
        return (log.date < savedDate && log.distance > distance) || (log.date > savedDate && log.distance < distance)
    }

    private fun deleteLog() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val bike = args.currentBike
                val distanceLogList = bike.logs.distance.toMutableList()
                distanceLogList.removeAt(args.logIndex)
                bike.logs.distance = distanceLogList.sortedBy { log -> log.date }.reversed()
                bike.personal_km = getUpdatedBikeDistance(bike)
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
