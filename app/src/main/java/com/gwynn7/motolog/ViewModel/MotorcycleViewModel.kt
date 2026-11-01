package com.gwynn7.motolog.ViewModel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.gwynn7.motolog.deleteImage
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.Repository.MotorcycleRepository
import com.gwynn7.motolog.getResizedBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MotorcycleViewModel(application: Application) : AndroidViewModel(application) {
    val readAllData: LiveData<List<Motorcycle>>
    private val repository: MotorcycleRepository
    private val imageDirectory: File = File(application.filesDir, "pictures").apply { mkdirs() }
    private val imageSize = 1200
    private val listImageSize = 400

    init {
        val db = MotorcycleDatabase.getDatabase(application)
        repository = MotorcycleRepository(db)
        readAllData = repository.readAllData
    }

    fun addMotorcycle(motorcycle: Motorcycle, bitmap: Bitmap?) {
        viewModelScope.launch(Dispatchers.IO) {
            if(bitmap != null) {
                motorcycle.image = saveImage(bitmap, imageSize)
                motorcycle.listImage = saveImage(bitmap, listImageSize)
            }
            repository.addMotorcycle(motorcycle)
        }
    }

    fun updateMotorcycle(
        motorcycle: Motorcycle,
        bitmap: Bitmap?,
        removeImageOnNull: Boolean = false,
        distanceLogsToDelete: List<DistanceLog> = emptyList()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if(bitmap != null){
                deleteImages(motorcycle)
                motorcycle.image = saveImage(bitmap, imageSize)
                motorcycle.listImage = saveImage(bitmap, listImageSize)
            }
            else if(removeImageOnNull) {
                deleteImages(motorcycle)
                motorcycle.image = null
                motorcycle.listImage = null
            }

            repository.updateMotorcycle(motorcycle, distanceLogsToDelete)
        }
    }

    fun deleteMotorcycle(motorcycle: Motorcycle) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMotorcycle(motorcycle)
            deleteImages(motorcycle)
        }
    }

    fun getMotorcycle(id: Int): LiveData<Motorcycle?> = repository.getMotorcycle(id)

    // Distance log operations
    fun getDistanceLogs(bikeId: Int): LiveData<List<DistanceLog>> = repository.getDistanceLogs(bikeId)

    fun saveDistanceLog(log: DistanceLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveDistanceLog(log)
        }
    }

    fun deleteDistanceLog(log: DistanceLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDistanceLog(log)
        }
    }

    fun getRepairsLogs(bikeId: Int): LiveData<List<RepairsLog>> = repository.getRepairsLogs(bikeId)

    fun saveRepairsLog(log: RepairsLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveRepairsLog(log)
        }
    }

    fun deleteRepairsLog(log: RepairsLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRepairsLog(log)
        }
    }

    fun getModsLogs(bikeId: Int): LiveData<List<ModsLog>> = repository.getModsLogs(bikeId)

    fun saveModsLog(log: ModsLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveModsLog(log)
        }
    }

    fun deleteModsLog(log: ModsLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteModsLog(log)
        }
    }

    private fun saveImage(bitmap: Bitmap, size: Int): Uri {
        val filePath = File(imageDirectory, String.format("%s.jpg", UUID.randomUUID().toString()))
        FileOutputStream(filePath).use { outputStream ->
            getResizedBitmap(bitmap, size).compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        }
        return filePath.toUri()
    }

    private fun deleteImages(motorcycle: Motorcycle){
        deleteImage(motorcycle.image)
        deleteImage(motorcycle.listImage)
    }

    var selectedBikeId: Int? = null
}
