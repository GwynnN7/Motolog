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
import java.util.Calendar
import java.util.Date

class ModsLogAddFragment : Fragment(), MenuProvider {
    private var _binding: ModslogAddBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ModsLogAddFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private var savedDate: Long = Date().time
    private var currentPath: Path = Path.Add

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModslogAddBinding.inflate(inflater, container, false)


        binding.btDeleteMod.setOnClickListener {
            deleteLog()
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (args.logIndex != -1) currentPath = Path.Edit

        if (currentPath == Path.Edit) {
            val currentLog = args.currentBike.logs.mods[args.logIndex]
            savedDate = currentLog.date

            binding.etModTitle.setText(currentLog.title)
            binding.etModTitle.isSelected = true

            binding.etModDescription.setText(currentLog.description)
            binding.etModPrice.setText(currentLog.price.toString())
        }

        binding.dpModDate.maxDate = Date().time
        binding.dpModDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth ->
            savedDate = longFromDate(year, month, dayOfMonth)
        }

        binding.btDeleteMod.visibility = if (currentPath == Path.Edit) View.VISIBLE else View.GONE
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
        val title = binding.etModTitle.text.toString().trim()
        val description = binding.etModDescription.text.toString().trim()
        val price = binding.etModPrice.text.toString()

        if (inputCheck(title, description, price)) {
            val bike = args.currentBike
            val modsLogList = bike.logs.mods.toMutableList()
            val newLog = ModsLog(title, description, savedDate, price.toDouble())

            if (currentPath == Path.Edit) modsLogList[args.logIndex] = newLog
            else modsLogList.add(0, newLog)

            bike.logs.mods = modsLogList.sortedBy { log -> log.date }.reversed()
            mMotorcycleViewModel.updateMotorcycle(bike, null)

            showToast(requireContext(), getString(R.string.log_saved))
            findNavController().navigateUp()
        } else showToast(requireContext(), getString(R.string.fill_fields))
    }

    private fun inputCheck(title: String, description: String, price: String): Boolean {
        return title.isNotEmpty() && description.isNotEmpty() && price.isNotEmpty()
    }

    private fun deleteLog() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val bike = args.currentBike
                val modsLogList = bike.logs.mods.toMutableList()
                modsLogList.removeAt(args.logIndex)
                bike.logs.mods = modsLogList
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
