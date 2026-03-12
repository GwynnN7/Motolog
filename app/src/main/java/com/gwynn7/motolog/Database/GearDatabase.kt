package com.gwynn7.motolog.Database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gwynn7.motolog.Models.Gear

@Database(entities = [Gear::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class GearDatabase : RoomDatabase() {
    abstract fun gearDao(): GearDAO

    companion object {
        @Volatile
        private var INSTANCE: GearDatabase? = null

        fun getDatabase(context: Context): GearDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GearDatabase::class.java,
                    "gear_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}