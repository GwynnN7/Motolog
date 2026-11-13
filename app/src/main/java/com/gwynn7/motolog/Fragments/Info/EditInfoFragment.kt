package com.gwynn7.motolog.Fragments.Info

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
import com.gwynn7.motolog.databinding.InfoBikeinfoEditBinding
import com.gwynn7.motolog.saveChanges
import com.gwynn7.motolog.showToast

class EditInfoFragment : Fragment(R.layout.info_bikeinfo_edit), MenuProvider {
    private var binding: InfoBikeinfoEditBinding? = null
    private val args by navArgs<EditInfoFragmentArgs>()
    private val model: MotorcycleViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = InfoBikeinfoEditBinding.bind(view).also { binding = it }
        val tires = arguments?.getString("editSection") == "tires"
        ui.editorHeading.setText(if (tires) R.string.profile_tires else R.string.info)
        (ui.etBikeFrontTire.parent.parent as View).isVisible = tires
        (ui.etBikeRearTire.parent.parent as View).isVisible = tires
        (ui.etBikeLicensePlate.parent.parent as View).isVisible = !tires
        (ui.etBikePrice.parent.parent as View).isVisible = !tires
        val info = args.currentBike.info
        ui.etBikePrice.setText(info.price.takeIf { it > 0 }?.toString().orEmpty())
        ui.etBikeLicensePlate.setText(info.plate_number)
        ui.etBikeFrontTire.setText(info.front_tire)
        ui.etBikeRearTire.setText(info.rear_tire)
        ui.saveProfile.setOnClickListener { save() }
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) { inflater.inflate(R.menu.save_menu, menu) }
    override fun onMenuItemSelected(item: MenuItem): Boolean = if (item.itemId == R.id.save_menu) { save(); true } else false

    private fun save() {
        val ui = binding ?: return
        val price = ui.etBikePrice.text.toString().trim().ifBlank { "0" }.replace(',', '.').toDoubleOrNull()
        ui.etBikePrice.error = if (price == null || !price.isFinite() || price < 0) getString(R.string.profile_invalid_number) else null
        if (price == null || !price.isFinite() || price < 0) return
        val info = if (arguments?.getString("editSection") == "tires") args.currentBike.info.copy(
            front_tire = ui.etBikeFrontTire.text.toString().trim(), rear_tire = ui.etBikeRearTire.text.toString().trim())
        else args.currentBike.info.copy(plate_number = ui.etBikeLicensePlate.text.toString().trim().uppercase(java.util.Locale.ROOT), price = price)
        val bike = args.currentBike.copy(info = info)
        saveChanges(model.updateMotorcycle(bike, null)) {
            showToast(requireContext(), getString(R.string.info_saved))
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
