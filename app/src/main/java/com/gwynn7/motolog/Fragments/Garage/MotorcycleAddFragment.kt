package com.gwynn7.motolog.Fragments.Garage

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.getUpdatedBikeDistance
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.capitalize
import com.gwynn7.motolog.databinding.MotorcycleAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.showToast
import java.util.Calendar

class MotorcycleAddFragment : Fragment() {
    private var _binding: MotorcycleAddBinding? = null
    private val binding get() = _binding!!
    private val mMotorcycleViewModel: MotorcycleViewModel by viewModels()
    private val args by navArgs<MotorcycleAddFragmentArgs>()
    private var currentPath: Path = Path.Add
    private var tempBitmap: Bitmap? = null
    private var bShouldRemoveImage: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MotorcycleAddBinding.inflate(inflater, container, false)

        if (args.currentMotorcycle != null) currentPath = Path.Edit

        binding.textViewBikeDistancePre.text = capitalize(getString(R.string.distance_when_bought, UnitHelper.getDistanceText(requireContext())))

        if (currentPath == Path.Edit) {
            val bike = args.currentMotorcycle!!
            binding.etBikeManufacturer.setText(bike.manufacturer)
            binding.etBikeModel.setText(bike.model)
            binding.etBikeAlias.setText(bike.alias)
            binding.etBikeYear.setText(bike.year.toString())
            binding.etBikeStartkm.setText(bike.start_km.toString())

            if (bike.image != null) binding.ibBikeImage.setImageURI(bike.image)
            else binding.ibBikeImage.setImageResource(R.drawable.add_photo)
        }

        binding.btDeleteMotorcycle.visibility = if (currentPath == Path.Edit) View.VISIBLE else View.INVISIBLE
        binding.btDeleteMotorcycle.setOnClickListener {
            deleteMotorcycle()
        }

        binding.ibBikeImage.setOnClickListener {
            uploadImage()
        }

        binding.ibBikeImage.setOnLongClickListener {
            tempBitmap = null
            bShouldRemoveImage = true
            binding.ibBikeImage.setImageResource(R.drawable.add_photo)
            true
        }

        requireActivity().addMenuProvider(object : MenuProvider {
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
        }, viewLifecycleOwner)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun insertDataToDatabase() {
        val manufacturer = binding.etBikeManufacturer.text.toString().trim()
        val model = binding.etBikeModel.text.toString().trim()
        val name = binding.etBikeAlias.text.toString().trim()
        val year = binding.etBikeYear.text.toString()
        val startKm = binding.etBikeStartkm.text.toString()

        if (inputCheck(manufacturer, model, year)) {
            val km = if (startKm.isEmpty()) 0 else startKm.toInt()
            val yearInt = year.toInt()

            if (yearInt > Calendar.getInstance().get(Calendar.YEAR)) {
                showToast(requireContext(), getString(R.string.bike_future))
                return
            }
            if (yearInt < 1900) {
                showToast(requireContext(), getString(R.string.bike_past))
                return
            }
            if (currentPath == Path.Edit) {
                val motorcycle = args.currentMotorcycle!!.copy(
                    manufacturer = manufacturer, model = model, alias = name,
                    year = yearInt, start_km = km, personal_km = 0
                )

                if (motorcycle.logs.distance.any { log -> distanceLogsCheck(log, motorcycle.start_km, motorcycle.year) }) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setPositiveButton(getString(R.string.delete_logs)) { _, _ ->
                            motorcycle.logs.distance = motorcycle.logs.distance.filter { log ->
                                !distanceLogsCheck(log, motorcycle.start_km, motorcycle.year)
                            }
                            motorcycle.personal_km = getUpdatedBikeDistance(motorcycle)
                            mMotorcycleViewModel.updateMotorcycle(motorcycle, tempBitmap, bShouldRemoveImage)
                            showToast(requireContext(), getString(R.string.bike_saved))
                            findNavController().navigateUp()
                        }
                        .setNegativeButton(getString(R.string.back), null)
                        .setTitle(getString(R.string.log_mismatch))
                        .setMessage(getString(R.string.log_mismatch_action))
                        .show()
                    return
                } else {
                    motorcycle.personal_km = getUpdatedBikeDistance(motorcycle)
                    mMotorcycleViewModel.updateMotorcycle(motorcycle, tempBitmap, bShouldRemoveImage)
                }
                showToast(requireContext(), getString(R.string.bike_saved))
            } else {
                val motorcycle = Motorcycle(0, manufacturer, model, name, yearInt, km)
                mMotorcycleViewModel.addMotorcycle(motorcycle, tempBitmap)
                showToast(requireContext(), getString(R.string.bike_add), Toast.LENGTH_LONG)
            }

            findNavController().navigateUp()
        } else showToast(requireContext(), getString(R.string.fill_fields))
    }

    private fun inputCheck(manufacturer: String, model: String, year: String): Boolean {
        return manufacturer.isNotEmpty() && model.isNotEmpty() && year.isNotEmpty()
    }

    private fun distanceLogsCheck(log: DistanceLog, startKm: Int, bikeYear: Int): Boolean {
        return dateFromLong(log.date, Calendar.YEAR) < bikeYear || log.distance < startKm
    }

    private fun deleteMotorcycle() {
        val currentBike = args.currentMotorcycle!!
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                mMotorcycleViewModel.deleteMotorcycle(currentBike)
                showToast(requireContext(), getString(R.string.bike_delete))
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${currentBike.manufacturer} ${currentBike.model}?")
            .setMessage(getString(R.string.delete_bike_question))
            .show()
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val options = CropImageContractOptions(
                uri,
                CropImageOptions(
                    imageSourceIncludeGallery = false,
                    imageSourceIncludeCamera = false
                )
            )
            cropImage.launch(options)
        }
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            val uriContent = result.uriContent!!
            val bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(requireContext().contentResolver, uriContent)
            )
            binding.ibBikeImage.setImageBitmap(bitmap)
            tempBitmap = bitmap

            MaterialAlertDialogBuilder(requireContext())
                .setPositiveButton(getString(R.string.ok), null)
                .setTitle(getString(R.string.image_saved))
                .setMessage(getString(R.string.image_saved_text))
                .show()
        }
    }

    private fun uploadImage() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
