package com.gwynn7.motolog.ViewModel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.Repository.GearRepository
import com.gwynn7.motolog.deleteImage
import com.gwynn7.motolog.getResizedBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GearViewModel(application: Application) : AndroidViewModel(application) {
    val readAllData: LiveData<List<Gear>>
    private val repository: GearRepository
    private val imageDirectory: File
    private val imageSize = 1200
    private val listImageSize = 350

    init {
        val gearDao = GearDatabase.getDatabase(application).gearDao()
        imageDirectory = File(application.filesDir, "pictures").apply { mkdirs() }
        repository = GearRepository(gearDao)
        readAllData = repository.readAllData
    }

    fun addGear(gear: Gear, bitmap: Bitmap?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (bitmap != null) {
                gear.image = saveImage(bitmap, imageSize)
                gear.listImage = saveImage(bitmap, listImageSize)
            }
            repository.addGear(gear)
        }
    }

    fun updateGear(gear: Gear, bitmap: Bitmap?, removeImageOnNull: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (bitmap != null) {
                deleteImage(gear)
                gear.image = saveImage(bitmap, imageSize)
                gear.listImage = saveImage(bitmap, listImageSize)
            } else if (removeImageOnNull) {
                deleteImage(gear)
                gear.image = null
                gear.listImage = null
            }
            repository.updateGear(gear)
        }
    }

    fun deleteGear(gear: Gear) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGear(gear)
            deleteImage(gear)
        }
    }

    fun getGear(id: Int): LiveData<Gear?> = repository.getGear(id)

    private fun saveImage(bitmap: Bitmap, size: Int): Uri {
        val filePath = File(imageDirectory, "${UUID.randomUUID()}.jpg")
        FileOutputStream(filePath).use { outputStream ->
            getResizedBitmap(bitmap, size).compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        }
        return filePath.toUri()
    }

    private fun deleteImage(gear: Gear){
        deleteImage(gear.image)
        deleteImage(gear.listImage)
    }
}
