package com.gwynn7.motolog.Fragments.Garage

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
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.photos.PhotoEditor
import com.gwynn7.motolog.photos.PhotoEditorViewModel
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.MotorcycleAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.saveChanges
import com.gwynn7.motolog.showToast
import java.util.Calendar

class MotorcycleAddFragment : Fragment(R.layout.motorcycle_add), MenuProvider {
    private var binding: MotorcycleAddBinding? = null
    private val model: MotorcycleViewModel by viewModels()
    private val args by navArgs<MotorcycleAddFragmentArgs>()
    private val photoModel: PhotoEditorViewModel by viewModels()
    private val photoEditor = PhotoEditor(this) { photoModel }
    private var distanceLogs: List<DistanceLog> = emptyList()
    private var logsLoaded = false
    private var draft: Bundle? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = MotorcycleAddBinding.bind(view).also { binding = it }
        val bike = args.currentMotorcycle
        draft = savedInstanceState?.getBundle("draft") ?: draft
        ui.editorHeading.setText(if (bike == null) R.string.add_motorcycle else R.string.edit_motorcycle)
        ui.odometerInput.hint = getString(R.string.profile_initial_odometer, UnitHelper.getDistance())
        ui.etBikeManufacturer.setText(draft?.getString("manufacturer") ?: bike?.manufacturer.orEmpty())
        ui.etBikeModel.setText(draft?.getString("model") ?: bike?.model.orEmpty())
        ui.etBikeAlias.setText(draft?.getString("alias") ?: bike?.alias.orEmpty())
        ui.etBikeYear.setText(draft?.getString("year") ?: bike?.year?.toString().orEmpty())
        ui.etBikeStartkm.setText(draft?.getString("odometer") ?: (bike?.start_km ?: 0).toString())
        logsLoaded = bike == null
        ui.saveProfile.isEnabled = logsLoaded
        if (bike != null) model.getDistanceLogs(bike.id).observe(viewLifecycleOwner) { logs ->
            distanceLogs = logs; logsLoaded = true; ui.saveProfile.isEnabled = true
        }
        ui.btDeleteMotorcycle.isVisible = bike != null
        ui.btDeleteMotorcycle.setOnClickListener { deleteMotorcycle() }
        ui.saveProfile.setOnClickListener { save() }
        ui.editPhoto.setOnClickListener { ui.ibBikeImage.performClick() }
        photoEditor.bind(ui.ibBikeImage, bike?.image) {
            showToast(requireContext(), getString(R.string.bike_saved))
            findNavController().navigateUp()
        }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.save_menu, menu) }
    override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.save_menu) { save(); true } else false

    private fun save() {
        val ui = binding ?: return
        if (!logsLoaded) return
        val manufacturer = ui.etBikeManufacturer.text.toString().trim()
        val name = ui.etBikeModel.text.toString().trim()
        val alias = ui.etBikeAlias.text.toString().trim()
        val year = ui.etBikeYear.text.toString().trim().toIntOrNull()
        val odometer = ui.etBikeStartkm.text.toString().trim().ifBlank { "0" }.toIntOrNull()
        val validYear = year != null && year in 1900..Calendar.getInstance().get(Calendar.YEAR)
        ui.manufacturerInput.error = if (manufacturer.isBlank()) getString(R.string.profile_required) else null
        ui.modelInput.error = if (name.isBlank()) getString(R.string.profile_required) else null
        ui.yearInput.error = if (!validYear) getString(R.string.profile_invalid_year) else null
        ui.odometerInput.error = if (odometer == null || odometer < 0) getString(R.string.profile_invalid_number) else null
        if (manufacturer.isBlank() || name.isBlank() || !validYear || odometer == null || odometer < 0) return
        val original = args.currentMotorcycle
        val updated = original?.copy(manufacturer = manufacturer, model = name, alias = alias, year = year, start_km = odometer)
            ?: Motorcycle(manufacturer = manufacturer, model = name, alias = alias, year = year, start_km = odometer)
        if (original == null) { photoEditor.save { model.addMotorcycle(updated, photoEditor.draft) }; return }
        val invalid = distanceLogs.filter { dateFromLong(it.date, Calendar.YEAR) < year || it.distance < odometer }
        fun persist() { photoEditor.save { model.updateMotorcycle(updated, photoEditor.draft, photoEditor.removeImage, invalid) } }
        if (invalid.isEmpty()) persist()
        else MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.log_mismatch).setMessage(R.string.log_mismatch_action)
            .setNegativeButton(R.string.back, null).setPositiveButton(R.string.delete_logs) { _, _ -> persist() }.show()
    }

    private fun deleteMotorcycle() {
        val bike = args.currentMotorcycle ?: return
        MaterialAlertDialogBuilder(requireContext()).setTitle("${getString(R.string.delete)} ${bike.manufacturer} ${bike.model}?")
            .setMessage(R.string.delete_bike_question).setNegativeButton(R.string.no, null).setPositiveButton(R.string.yes) { _, _ ->
                saveChanges(model.deleteMotorcycle(bike)) {
                    showToast(requireContext(), getString(R.string.bike_delete))
                    (requireActivity() as com.gwynn7.motolog.MainActivity).returnToGarage()
                }
            }.show()
    }

    private fun captureDraft() = binding?.let { ui -> Bundle().apply {
        putString("manufacturer", ui.etBikeManufacturer.text?.toString()); putString("model", ui.etBikeModel.text?.toString())
        putString("alias", ui.etBikeAlias.text?.toString()); putString("year", ui.etBikeYear.text?.toString())
        putString("odometer", ui.etBikeStartkm.text?.toString())
    } } ?: draft

    override fun onSaveInstanceState(outState: Bundle) { draft = captureDraft(); outState.putBundle("draft", draft); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { draft = captureDraft(); binding = null; super.onDestroyView() }
}
