package com.gwynn7.motolog.Fragments.Gear

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
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.Path
import com.gwynn7.motolog.R
import com.gwynn7.motolog.ViewModel.GearViewModel
import com.gwynn7.motolog.databinding.GearAddBinding
import com.gwynn7.motolog.dateFromLong
import com.gwynn7.motolog.longFromDate
import com.gwynn7.motolog.showToast
import java.util.Calendar
import java.util.Date

class GearAddFragment : Fragment(), MenuProvider {
    private var _binding: GearAddBinding? = null
    private val binding get() = _binding!!
    private val mGearViewModel: GearViewModel by viewModels()
    private val args by navArgs<GearAddFragmentArgs>()
    private var savedDate: Long = Date().time
    private var currentPath: Path = Path.Add
    private var tempBitmap: Bitmap? = null
    private var bShouldRemoveImage = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GearAddBinding.inflate(inflater, container, false)

        binding.btDeleteGear.setOnClickListener {
            deleteGear()
        }

        binding.ibGearImage.setOnClickListener {
            uploadImage()
        }
        binding.ibGearImage.setOnLongClickListener {
            tempBitmap = null
            bShouldRemoveImage = true
            binding.ibGearImage.setImageResource(R.drawable.add_photo)
            true
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (args.currentGear != null) currentPath = Path.Edit

        if (currentPath == Path.Edit) {
            val currentGear = args.currentGear!!
            savedDate = currentGear.date

            binding.etGearManufacturer.setText(currentGear.manufacturer)
            binding.etGearModel.setText(currentGear.model)
            binding.etGearPrice.setText(currentGear.price.toString())

            if (currentGear.image != null) binding.ibGearImage.setImageURI(currentGear.image)
            else binding.ibGearImage.setImageResource(R.drawable.add_photo)
        }

        binding.dpGearDate.maxDate = Date().time
        binding.dpGearDate.init(
            dateFromLong(savedDate, Calendar.YEAR),
            dateFromLong(savedDate, Calendar.MONTH),
            dateFromLong(savedDate, Calendar.DAY_OF_MONTH)
        ) { _, year, month, dayOfMonth ->
            savedDate = longFromDate(year, month, dayOfMonth)
        }

        binding.btDeleteGear.visibility = if (currentPath == Path.Edit) View.VISIBLE else View.GONE
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
        val manufacturer = binding.etGearManufacturer.text.toString().trim()
        val model = binding.etGearModel.text.toString().trim()
        val price = binding.etGearPrice.text.toString()

        if (inputCheck(manufacturer, model, price)) {
            if (currentPath == Path.Add) {
                mGearViewModel.addGear(Gear(0, manufacturer, model, price.toDouble(), savedDate), tempBitmap)
                showToast(requireContext(), getString(R.string.gear_added), Toast.LENGTH_LONG)
            } else {
                val updatedGear = args.currentGear!!.copy(
                    manufacturer = manufacturer, model = model,
                    price = price.toDouble(), date = savedDate
                )
                mGearViewModel.updateGear(updatedGear, tempBitmap, bShouldRemoveImage)
                showToast(requireContext(), getString(R.string.gear_save))
            }
            findNavController().navigateUp()
        } else showToast(requireContext(), getString(R.string.fill_fields))
    }

    private fun inputCheck(manufacturer: String, model: String, price: String): Boolean {
        return manufacturer.isNotEmpty() && model.isNotEmpty() && price.isNotEmpty()
    }

    private fun deleteGear() {
        val currentGear = args.currentGear!!
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                mGearViewModel.deleteGear(currentGear)
                showToast(requireContext(), getString(R.string.gear_delete))
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.no), null)
            .setTitle("${getString(R.string.delete)} ${currentGear.manufacturer} ${currentGear.model}?")
            .setMessage(getString(R.string.delete_gear_question))
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
            binding.ibGearImage.setImageBitmap(bitmap)
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
