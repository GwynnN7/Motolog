package com.gwynn7.motolog.Fragments.Gear

import android.os.Bundle
import com.gwynn7.motolog.saveChanges
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.photos.PhotoLoader
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.GearViewModel
import com.gwynn7.motolog.databinding.GearShowBinding
import com.gwynn7.motolog.longToDateString
import com.gwynn7.motolog.ui.calendarDaysBetween
import com.gwynn7.motolog.ui.formatLogInterval
import com.gwynn7.motolog.ui.formatMoney
import androidx.core.view.isVisible

class GearShowFragment : Fragment(), MenuProvider {
    private var _binding: GearShowBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<GearShowFragmentArgs>()
    private val mGearViewModel: GearViewModel by viewModels()
    private lateinit var currentGear: Gear

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GearShowBinding.inflate(inflater, container, false)

        binding.editGear.setOnClickListener { editGear() }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gearData = mGearViewModel.getGear(args.currentGear.id)
        gearData.observe(viewLifecycleOwner) { gear ->
            if (gear != null) {
                currentGear = gear

                binding.twGearModel.text = currentGear.model
                binding.twGearManufacturer.text = currentGear.manufacturer
                binding.twGearPrice.text = formatMoney(currentGear.price)
                binding.twGearDate.text = longToDateString(currentGear.date)
                val days = calendarDaysBetween(currentGear.date, System.currentTimeMillis())
                binding.gearAge.isVisible = days >= 0
                binding.gearAge.text = if (days == 0L) getString(R.string.profile_purchased_today)
                    else getString(R.string.profile_owned_for, requireContext().formatLogInterval(days))

                PhotoLoader.load(binding.ivGearImageShow, currentGear.image, R.drawable.helmet_show)

                binding.gearShowView.visibility = View.VISIBLE
            } else findNavController().navigateUp()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.show_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (!::currentGear.isInitialized) return false
        when (menuItem.itemId) {
            R.id.edit_show_menu -> {
                editGear()
                return true
            }
            R.id.delete_show_menu -> {
                deleteGear()
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun editGear() {
        if (::currentGear.isInitialized) findNavController().navigate(GearShowFragmentDirections.gearshowToGearadd(currentGear))
    }

    private fun deleteGear() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                saveChanges(mGearViewModel.deleteGear(currentGear)) {
                    Toast.makeText(requireContext(), getString(R.string.gear_delete), Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.gear_list, false)
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${args.currentGear.manufacturer} ${args.currentGear.model}?")
            .setMessage(getString(R.string.delete_gear_question))
            .show()
    }
}
