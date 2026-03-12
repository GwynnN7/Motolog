package com.gwynn7.motolog.Fragments.Gear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.GearViewModel
import com.gwynn7.motolog.databinding.GearShowBinding
import com.gwynn7.motolog.longToDateString

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

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val gearData = mGearViewModel.getGear(args.currentGear.id)
        gearData.observe(viewLifecycleOwner) { gears ->
            if (gears.isNotEmpty()) {
                currentGear = gears.first()

                if (currentGear.image != null && !currentGear.image!!.toFile().exists()) {
                    mGearViewModel.updateGear(currentGear, null, true)
                }

                binding.twGearModel.text = currentGear.model
                binding.twGearManufacturer.text = currentGear.manufacturer
                binding.twGearPrice.text = String.format("%.2f%s", currentGear.price, UnitHelper.getCurrency())
                binding.twGearDate.text = longToDateString(currentGear.date)

                if (currentGear.image != null) binding.ivGearImageShow.setImageURI(currentGear.image)
                else binding.ivGearImageShow.setImageResource(R.drawable.helmet_show)

                binding.gearShowView.visibility = View.VISIBLE
            } else findNavController().navigateUp()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.show_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.edit_show_menu -> {
                val action = GearShowFragmentDirections.gearshowToGearadd(currentGear)
                findNavController().navigate(action)
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

    private fun deleteGear() {
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                mGearViewModel.deleteGear(args.currentGear)
                Toast.makeText(requireContext(), getString(R.string.gear_delete), Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.gearshow_to_gearlist)
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${args.currentGear.manufacturer} ${args.currentGear.model}?")
            .setMessage(getString(R.string.delete_gear_question))
            .show()
    }
}
