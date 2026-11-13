package com.gwynn7.motolog.Fragments.Gear

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.photos.PhotoEditor
import com.gwynn7.motolog.photos.PhotoEditorViewModel
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.GearViewModel
import com.gwynn7.motolog.databinding.GearAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.saveChanges
import com.gwynn7.motolog.showToast
import java.util.Calendar

class GearAddFragment : Fragment(R.layout.gear_add), MenuProvider {
    private var binding: GearAddBinding? = null
    private val model: GearViewModel by viewModels()
    private val args by navArgs<GearAddFragmentArgs>()
    private var savedDate = System.currentTimeMillis()
    private var draft: Bundle? = null
    private var dateDialog: DatePickerDialog? = null
    private val photoModel: PhotoEditorViewModel by viewModels()
    private val photoEditor = PhotoEditor(this) { photoModel }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = GearAddBinding.bind(view).also { binding = it }
        val gear = args.currentGear
        draft = savedInstanceState?.getBundle("draft") ?: draft
        savedDate = draft?.getLong("date") ?: savedInstanceState?.takeIf { it.containsKey("gearDate") }?.getLong("gearDate") ?: gear?.date ?: savedDate
        ui.editorHeading.setText(if (gear == null) R.string.gear_add else R.string.gear_edit)
        ui.etGearManufacturer.setText(draft?.getString("manufacturer") ?: gear?.manufacturer.orEmpty())
        ui.etGearModel.setText(draft?.getString("model") ?: gear?.model.orEmpty())
        ui.etGearPrice.setText(draft?.getString("price") ?: gear?.price?.toString().orEmpty())
        ui.costInput.prefixText = UnitHelper.getCurrency() + " "
        showDate()
        ui.purchaseDate.setOnClickListener { chooseDate() }
        ui.saveProfile.setOnClickListener { save() }
        ui.btDeleteGear.isVisible = gear != null
        ui.btDeleteGear.setOnClickListener { deleteGear() }
        ui.editPhoto.setOnClickListener { ui.ibGearImage.performClick() }
        photoEditor.bind(ui.ibGearImage, gear?.image) {
            showToast(requireContext(), getString(R.string.gear_save))
            findNavController().navigateUp()
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.save_menu, menu) }
    override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.save_menu) { save(); true } else false

    private fun showDate() { binding?.purchaseDate?.text = getString(R.string.profile_purchase_date, longToDateString(savedDate)) }
    private fun chooseDate() {
        dateDialog?.dismiss()
        dateDialog = DatePickerDialog(requireContext(), { _, year, month, day ->
            savedDate = longFromDate(year, month, day); showDate()
        }, dateFromLong(savedDate, Calendar.YEAR), dateFromLong(savedDate, Calendar.MONTH), dateFromLong(savedDate, Calendar.DAY_OF_MONTH))
            .apply { datePicker.maxDate = System.currentTimeMillis(); show() }
    }

    private fun save() {
        val ui = binding ?: return
        val manufacturer = ui.etGearManufacturer.text.toString().trim()
        val name = ui.etGearModel.text.toString().trim()
        val price = ui.etGearPrice.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        ui.manufacturerInput.error = if (manufacturer.isBlank()) getString(R.string.profile_required) else null
        ui.modelInput.error = if (name.isBlank()) getString(R.string.profile_required) else null
        ui.costInput.error = if (price == null || !price.isFinite() || price < 0) getString(R.string.profile_invalid_number) else null
        if (manufacturer.isBlank() || name.isBlank() || price == null || !price.isFinite() || price < 0) return
        val original = args.currentGear
        if (original == null) photoEditor.save { model.addGear(Gear(manufacturer = manufacturer, model = name, price = price, date = savedDate), photoEditor.draft) }
        else photoEditor.save { model.updateGear(original.copy(manufacturer = manufacturer, model = name, price = price, date = savedDate), photoEditor.draft, photoEditor.removeImage) }
    }

    private fun deleteGear() {
        val gear = args.currentGear ?: return
        MaterialAlertDialogBuilder(requireContext()).setTitle("${getString(R.string.delete)} ${gear.manufacturer} ${gear.model}?")
            .setMessage(R.string.delete_gear_question).setNegativeButton(R.string.no, null).setPositiveButton(R.string.yes) { _, _ ->
                saveChanges(model.deleteGear(gear)) {
                    showToast(requireContext(), getString(R.string.gear_delete))
                    findNavController().popBackStack(R.id.gear_list, false)
                }
            }.show()
    }

    private fun captureDraft() = binding?.let { ui -> Bundle().apply {
        putLong("date", savedDate); putString("manufacturer", ui.etGearManufacturer.text?.toString())
        putString("model", ui.etGearModel.text?.toString()); putString("price", ui.etGearPrice.text?.toString())
    } } ?: draft
    override fun onSaveInstanceState(outState: Bundle) { draft = captureDraft(); outState.putBundle("draft", draft); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { dateDialog?.dismiss(); dateDialog = null; draft = captureDraft(); binding = null; super.onDestroyView() }
}
