package com.gwynn7.motolog.Fragments.Info

import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.InfoEngineinfoEditBinding
import com.gwynn7.motolog.saveChanges
import com.gwynn7.motolog.showToast

class EditEngineFragment : Fragment(R.layout.info_engineinfo_edit), MenuProvider {
    private var binding: InfoEngineinfoEditBinding? = null
    private val args by navArgs<EditEngineFragmentArgs>()
    private val model: MotorcycleViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = InfoEngineinfoEditBinding.bind(view).also { binding = it }
        val info = args.currentBike.info
        ui.etBikeCc.setText(info.engine_cc.takeIf { it > 0 }?.toString().orEmpty())
        ui.etBikeHp.setText(info.horse_power.takeIf { it > 0 }?.toString().orEmpty())
        ui.etBikeCylinders.setText(info.cylinders.takeIf { it > 0 }?.toString().orEmpty())
        ui.etBikeTorque.setText(info.torque.takeIf { it > 0 }?.toString().orEmpty())
        ui.saveProfile.setOnClickListener { save() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.save_menu, menu) }
    override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.save_menu) { save(); true } else false

    private fun save() {
        val ui = binding ?: return
        val cylinders = ui.etBikeCylinders.text.toString().trim().ifBlank { "0" }.toIntOrNull()
        val cc = ui.etBikeCc.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        val torque = ui.etBikeTorque.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        val power = ui.etBikeHp.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        ui.etBikeCylinders.error = if (cylinders == null || cylinders < 0) getString(R.string.profile_invalid_number) else null
        for ((field, value) in listOf(ui.etBikeCc to cc, ui.etBikeTorque to torque, ui.etBikeHp to power)) {
            field.error = if (value == null || !value.isFinite() || value < 0) getString(R.string.profile_invalid_number) else null
        }
        if (cylinders == null || cylinders < 0 || listOf(cc, torque, power).any { it == null || !it.isFinite() || it < 0 }) return
        val bike = args.currentBike.copy(info = args.currentBike.info.copy(
            cylinders = cylinders, engine_cc = cc!!, torque = torque!!, horse_power = power!!))
        saveChanges(model.updateMotorcycle(bike, null)) {
            showToast(requireContext(), getString(R.string.info_saved))
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
