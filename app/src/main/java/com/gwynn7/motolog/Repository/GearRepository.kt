package com.gwynn7.motolog.Repository

import androidx.lifecycle.LiveData
import com.gwynn7.motolog.Database.GearDAO
import com.gwynn7.motolog.Models.Gear

class GearRepository(private val gearDao: GearDAO) {
    val readAllData: LiveData<List<Gear>> = gearDao.readAllData()

    suspend fun addGear(gear: Gear) {
        gearDao.addGear(gear)
    }

    suspend fun updateGear(gear: Gear) {
        gearDao.updateGear(gear)
    }

    suspend fun deleteGear(gear: Gear) {
        gearDao.deleteGear(gear)
    }

    fun getGear(id: Int): LiveData<List<Gear>> {
        return gearDao.getGear(id)
    }
}