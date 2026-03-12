package com.gwynn7.motolog.Database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gwynn7.motolog.Models.Motorcycle

@Database(entities = [Motorcycle::class], version = 14, exportSchema = true)
@TypeConverters(Converters::class)
abstract class MotorcycleDatabase : RoomDatabase() {
    abstract fun motorcycleDao(): MotorcycleDAO

    companion object {
        @Volatile
        private var INSTANCE: MotorcycleDatabase? = null

        fun getDatabase(context: Context): MotorcycleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MotorcycleDatabase::class.java,
                    "motorcycles_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}