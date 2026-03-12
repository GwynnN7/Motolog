package com.gwynn7.motolog.Repository

import androidx.lifecycle.LiveData
import com.gwynn7.motolog.Database.MotorcycleDAO
import com.gwynn7.motolog.Models.Motorcycle

class MotorcycleRepository(private val motorcycleDao: MotorcycleDAO) {
    val readAllData: LiveData<List<Motorcycle>> = motorcycleDao.readAllData()

    suspend fun addMotorcycle(motorcycle: Motorcycle) {
        motorcycleDao.addMotorcycle(motorcycle)
    }

    suspend fun updateMotorcycle(motorcycle: Motorcycle) {
        motorcycleDao.updateMotorcycle(motorcycle)
    }

    suspend fun deleteMotorcycle(motorcycle: Motorcycle) {
        motorcycleDao.deleteMotorcycle(motorcycle)
    }

    fun getMotorcycle(id: Int): LiveData<List<Motorcycle>> {
        return motorcycleDao.getMotorcycle(id)
    }
}