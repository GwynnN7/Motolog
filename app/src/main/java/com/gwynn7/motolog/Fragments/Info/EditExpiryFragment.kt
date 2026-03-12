package com.gwynn7.motolog.Fragments.Info

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
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.InfoExpiryinfoEditBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.showToast
import java.util.Calendar
import java.util.Date

class EditExpiryFragment : Fragment(), MenuProvider {
    private var _binding: InfoExpiryinfoEditBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<EditExpiryFragmentArgs>()
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()

    private var taxDate: Long = Date().time
    private var insuranceDate: Long = Date().time
    private var inspectionDate: Long = Date().time

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoExpiryinfoEditBinding.inflate(inflater, container, false)

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (args.currentBike.expiry.tax >= 0) taxDate = args.currentBike.expiry.tax
        binding.dpTax.init(
            dateFromLong(taxDate, Calendar.YEAR),
            dateFromLong(taxDate, Calendar.MONTH),
            dateFromLong(taxDate, Calendar.DAY_OF_MONTH)
        ) { _, year, monthOfYear, dayOfMonth ->
            taxDate = longFromDate(year, monthOfYear, dayOfMonth)
        }

        if (args.currentBike.expiry.inspection >= 0) inspectionDate = args.currentBike.expiry.inspection
        binding.dpInspection.init(
            dateFromLong(inspectionDate, Calendar.YEAR),
            dateFromLong(inspectionDate, Calendar.MONTH),
            dateFromLong(inspectionDate, Calendar.DAY_OF_MONTH)
        ) { _, year, monthOfYear, dayOfMonth ->
            inspectionDate = longFromDate(year, monthOfYear, dayOfMonth)
        }

        if (args.currentBike.expiry.insurance >= 0) insuranceDate = args.currentBike.expiry.insurance
        binding.dpInsurance.init(
            dateFromLong(insuranceDate, Calendar.YEAR),
            dateFromLong(insuranceDate, Calendar.MONTH),
            dateFromLong(insuranceDate, Calendar.DAY_OF_MONTH)
        ) { _, year, monthOfYear, dayOfMonth ->
            insuranceDate = longFromDate(year, monthOfYear, dayOfMonth)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.save_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.save_menu) {
            val bike = args.currentBike
            bike.expiry.tax = taxDate
            bike.expiry.insurance = insuranceDate
            bike.expiry.inspection = inspectionDate
            mMotorcycleViewModel.updateMotorcycle(bike, null)
            showToast(requireContext(), getString(R.string.info_saved))
            findNavController().navigateUp()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
