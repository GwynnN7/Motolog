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
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Repository.MotorcycleRepository
import com.gwynn7.motolog.getResizedBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

class MotorcycleViewModel(application: Application) : AndroidViewModel(application) {
    val readAllData: LiveData<List<Motorcycle>>
    private val repository: MotorcycleRepository
    private val imageDirectory: File = File(application.filesDir, "pictures").apply { mkdirs() }
    private val imageSize = 1200
    private val listImageSize = 400

    init {
        val motorcycleDao = MotorcycleDatabase.getDatabase(application).motorcycleDao()
        repository = MotorcycleRepository(motorcycleDao)
        readAllData = repository.readAllData
    }

    fun addMotorcycle(motorcycle: Motorcycle, bitmap: Bitmap?){
        viewModelScope.launch {
            if(bitmap != null) {
                motorcycle.image = saveImage(bitmap, imageSize)
                motorcycle.listImage = saveImage(bitmap, listImageSize)
            }
            withContext(Dispatchers.IO) {
                repository.addMotorcycle(motorcycle)
            }
        }
    }

    fun updateMotorcycle(motorcycle: Motorcycle, bitmap: Bitmap?, removeImageOnNull: Boolean = false){
        viewModelScope.launch {
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

            withContext(Dispatchers.IO) {
                repository.updateMotorcycle(motorcycle)
            }
        }
    }

    fun deleteMotorcycle(motorcycle: Motorcycle){
        viewModelScope.launch {
            deleteImages(motorcycle)
            withContext(Dispatchers.IO){
                repository.deleteMotorcycle(motorcycle)
            }
        }
    }

    fun getMotorcycle(id: Int): LiveData<List<Motorcycle>>{
        return repository.getMotorcycle(id)
    }

    private suspend fun saveImage(bitmap: Bitmap, size: Int): Uri = withContext(Dispatchers.IO) {
        val filePath = File(imageDirectory, String.format("%s.jpg", UUID.randomUUID().toString()))
        val outputStream: OutputStream = FileOutputStream(filePath)
        getResizedBitmap(bitmap, size).compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        outputStream.flush()
        outputStream.close()
        filePath.toUri()
    }

    private fun deleteImages(motorcycle: Motorcycle){
        deleteImage(motorcycle.image)
        deleteImage(motorcycle.listImage)
    }

    companion object{
        var currentBikeId: Int? = null
    }
}