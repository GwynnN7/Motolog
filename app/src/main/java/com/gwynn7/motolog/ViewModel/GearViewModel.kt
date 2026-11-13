package com.gwynn7.motolog.ViewModel

import android.app.Application
import com.gwynn7.motolog.Database.DataAccess
import android.net.Uri
import com.gwynn7.motolog.photos.PhotoStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Models.Gear
import com.gwynn7.motolog.Repository.GearRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException

class GearViewModel(application: Application) : AndroidViewModel(application) {
    val readAllData: LiveData<List<Gear>>
    private val repository: GearRepository
    private val photos = PhotoStore(application)

    init {
        val gearDao = GearDatabase.getDatabase(application).gearDao()
        repository = GearRepository(gearDao)
        readAllData = repository.readAllData
    }

    fun addGear(gear: Gear, photo: Uri?) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                photos.commit(photo) { pair ->
                    val updated = pair?.let { gear.copy(image = it.first, listImage = it.second) } ?: gear
                    repository.addGear(updated)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun updateGear(gear: Gear, photo: Uri?, removeImageOnNull: Boolean = false) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                photos.commit(photo) { pair ->
                    val updated = when {
                        pair != null -> gear.copy(image = pair.first, listImage = pair.second)
                        removeImageOnNull -> gear.copy(image = null, listImage = null)
                        else -> gear
                    }
                    repository.updateGear(updated)
                }
                if (photo != null || removeImageOnNull) photos.deleteUnreferenced(gear.image, gear.listImage)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun deleteGear(gear: Gear) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                withContext(NonCancellable) {
                    repository.deleteGear(gear)
                    photos.deleteUnreferenced(gear.image, gear.listImage)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun getGear(id: Int): LiveData<Gear?> = repository.getGear(id)


}
