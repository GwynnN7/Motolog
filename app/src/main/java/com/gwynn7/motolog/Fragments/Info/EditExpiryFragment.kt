package com.gwynn7.motolog.Fragments.Info

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.InfoExpiryinfoEditBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.saveChanges
import com.gwynn7.motolog.showToast
import java.util.Calendar

class EditExpiryFragment : Fragment(R.layout.info_expiryinfo_edit), MenuProvider {
    private var binding: InfoExpiryinfoEditBinding? = null
    private val args by navArgs<EditExpiryFragmentArgs>()
    private val model: MotorcycleViewModel by viewModels()
    private var dates: LongArray? = null
    private var dialog: DatePickerDialog? = null
    private val labels = listOf(R.string.bike_insurance, R.string.bike_tax, R.string.bike_inspection)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = InfoExpiryinfoEditBinding.bind(view).also { binding = it }
        val expiry = args.currentBike.expiry
        dates = savedInstanceState?.getLongArray("dates")?.takeIf { it.size == 3 } ?: dates
            ?: longArrayOf(expiry.insurance, expiry.tax, expiry.inspection)
        val buttons = listOf(ui.insuranceDate, ui.taxDate, ui.inspectionDate)
        val clearButtons = listOf(ui.clearInsurance, ui.clearTax, ui.clearInspection)
        buttons.forEachIndexed { index, button -> button.setOnClickListener { chooseDate(index) } }
        clearButtons.forEachIndexed { index, button ->
            button.contentDescription = getString(R.string.profile_clear_named_date, getString(labels[index]))
            button.setOnClickListener { dates!![index] = -1; renderDates() }
        }
        renderDates()
        ui.saveProfile.setOnClickListener { save() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun renderDates() {
        val ui = binding ?: return
        listOf(ui.insuranceDate, ui.taxDate, ui.inspectionDate).forEachIndexed { index, button ->
            val date = dates!![index]
            button.text = if (date >= 0) longToDateString(date) else getString(R.string.profile_choose_date)
            button.contentDescription = getString(labels[index]) + ": " + if (date >= 0) button.text else getString(R.string.profile_no_date)
        }
        listOf(ui.clearInsurance, ui.clearTax, ui.clearInspection).forEachIndexed { index, button -> button.isVisible = dates!![index] >= 0 }
    }

    private fun chooseDate(index: Int) {
        val date = dates!![index].takeIf { it >= 0 } ?: System.currentTimeMillis()
        dialog?.dismiss()
        dialog = DatePickerDialog(requireContext(), { _, year, month, day ->
            dates!![index] = longFromDate(year, month, day); renderDates()
        }, dateFromLong(date, Calendar.YEAR), dateFromLong(date, Calendar.MONTH), dateFromLong(date, Calendar.DAY_OF_MONTH))
            .apply { setTitle(labels[index]); show() }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.save_menu, menu) }
    override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.save_menu) { save(); true } else false
    private fun save() {
        val values = dates ?: return
        val bike = args.currentBike.copy(expiry = args.currentBike.expiry.copy(insurance = values[0], tax = values[1], inspection = values[2]))
        saveChanges(model.updateMotorcycle(bike, null)) {
            showToast(requireContext(), getString(R.string.info_saved))
            findNavController().navigateUp()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putLongArray("dates", dates); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { dialog?.dismiss(); dialog = null; binding = null; super.onDestroyView() }
}
