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
import androidx.lifecycle.SavedStateHandle
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import com.gwynn7.motolog.Repository.MotorcycleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException

class MotorcycleViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val readAllData: LiveData<List<Motorcycle>>
    private val repository: MotorcycleRepository
    private val photos = PhotoStore(application)

    init {
        val db = MotorcycleDatabase.getDatabase(application)
        repository = MotorcycleRepository(db)
        readAllData = repository.readAllData
    }

    fun addMotorcycle(motorcycle: Motorcycle, photo: Uri?) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                photos.commit(photo) { pair ->
                    val updated = pair?.let { motorcycle.copy(image = it.first, listImage = it.second) } ?: motorcycle
                    repository.addMotorcycle(updated)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun updateMotorcycle(
        motorcycle: Motorcycle,
        photo: Uri?,
        removeImageOnNull: Boolean = false,
        distanceLogsToDelete: List<DistanceLog> = emptyList()
    ) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                photos.commit(photo) { pair ->
                    val updated = when {
                        pair != null -> motorcycle.copy(image = pair.first, listImage = pair.second)
                        removeImageOnNull -> motorcycle.copy(image = null, listImage = null)
                        else -> motorcycle
                    }
                    repository.updateMotorcycle(updated, distanceLogsToDelete)
                }
                if (photo != null || removeImageOnNull) photos.deleteUnreferenced(motorcycle.image, motorcycle.listImage)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun deleteMotorcycle(motorcycle: Motorcycle) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                withContext(NonCancellable) {
                    repository.deleteMotorcycle(motorcycle)
                    photos.deleteUnreferenced(motorcycle.image, motorcycle.listImage)
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun getMotorcycle(id: Int): LiveData<Motorcycle?> = repository.getMotorcycle(id)

    // Distance log operations
    fun getDistanceLogs(bikeId: Int): LiveData<List<DistanceLog>> = repository.getDistanceLogs(bikeId)

    fun saveDistanceLog(log: DistanceLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.saveDistanceLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun deleteDistanceLog(log: DistanceLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.deleteDistanceLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun getRepairsLogs(bikeId: Int): LiveData<List<RepairsLog>> = repository.getRepairsLogs(bikeId)

    fun saveRepairsLog(log: RepairsLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.saveRepairsLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun deleteRepairsLog(log: RepairsLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.deleteRepairsLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun getModsLogs(bikeId: Int): LiveData<List<ModsLog>> = repository.getModsLogs(bikeId)

    fun saveModsLog(log: ModsLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.saveModsLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    fun deleteModsLog(log: ModsLog) = viewModelScope.async(Dispatchers.IO) {
        try {
            DataAccess.write {
                repository.deleteModsLog(log)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }



    var selectedBikeId: Int?
        get() = savedState["bikeId"]
        set(value) { savedState["bikeId"] = value }
}
