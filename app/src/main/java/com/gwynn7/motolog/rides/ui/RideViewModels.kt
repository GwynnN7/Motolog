package com.gwynn7.motolog.rides.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.rides.data.RouteSnapshot
import com.gwynn7.motolog.rides.data.RideRepository
import com.gwynn7.motolog.rides.data.routePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import com.gwynn7.motolog.rides.tracking.RideRecorderService
import com.gwynn7.motolog.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class RideViewModel(application: Application) : AndroidViewModel(application) {
    protected val database = MotorcycleDatabase.getDatabase(application)
    protected val repository = RideRepository(database)
    protected val settings = AppSettings(application)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    fun clearError() { mutableError.value = null }
    protected fun perform(action: suspend () -> Unit) {
        if (mutableBusy.value) return
        viewModelScope.launch {
            mutableBusy.value = true
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = error.message ?: "Unable to save changes" }
            finally { mutableBusy.value = false }
        }
    }
}

class RidesViewModel(application: Application) : RideViewModel(application) {
    val rides = repository.rides.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun preview(id: String) = repository.points(id).map { routePreview(it) }.flowOn(Dispatchers.Default)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecorderViewModel(application: Application, private val savedState: SavedStateHandle) : RideViewModel(application) {
    val motorcycles = database.motorcycleDao().observeMotorcycles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedBike = savedState.getStateFlow("bikeId", -1)
    private val sessionId = savedState.getStateFlow<String?>("sessionId", null)
    val ride = sessionId.flatMapLatest { id -> id?.let(repository::observeRide) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val route = sessionId.flatMapLatest { id -> id?.let(repository::liveRoute) ?: flowOf(RouteSnapshot()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RouteSnapshot())
    val recorder = RideRecorderService.status
    val loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (selectedBike.value < 0) savedState["bikeId"] = settings.lastBike() ?: -1
            repository.activeRide.collect { active ->
                if (active != null) savedState["sessionId"] = active.id
                loaded.value = true
            }
        }
    }

    fun selectBike(id: Int) { savedState["bikeId"] = id }
    fun finishRecovered() = perform {
        val active = repository.getActiveRide() ?: return@perform
        repository.finish(active.id)
    }
}

class RideDetailsViewModel(application: Application, savedState: SavedStateHandle) : RideViewModel(application) {
    private val id: String = requireNotNull(savedState["rideId"])
    val ride = repository.observeRide(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val points = repository.points(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deleted = MutableStateFlow(false)
    suspend fun currentOdometer(): Int? = ride.value?.let { repository.currentOdometer(it) }
    fun confirmMileage(expected: Int, value: Int) = perform {
        repository.confirmMileage(id, expected, value, settings.distanceUnit())
    }
    fun skipMileage() = perform { repository.skipMileage(id) }
    fun delete() = perform { repository.delete(id); deleted.value = true }
}
