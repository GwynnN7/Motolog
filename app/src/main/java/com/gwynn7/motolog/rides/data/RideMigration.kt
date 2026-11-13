package com.gwynn7.motolog.rides.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration: published MotoLog tables and their primary keys are untouched. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `rides` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `motorcycleId` INTEGER,
                `motorcycleName` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER,
                `durationMillis` INTEGER NOT NULL,
                `distanceMeters` REAL NOT NULL,
                `state` TEXT NOT NULL,
                `activeSlot` INTEGER,
                `segment` INTEGER NOT NULL,
                `odometerAtStart` INTEGER NOT NULL,
                `odometerUnit` TEXT NOT NULL,
                `mileageDecision` TEXT NOT NULL,
                `distanceLogId` INTEGER,
                `confirmedOdometer` INTEGER,
                FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycle`(`id`) ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rides_motorcycleId` ON `rides` (`motorcycleId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rides_activeSlot` ON `rides` (`activeSlot`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `ride_points` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `rideId` TEXT NOT NULL,
                `recordedAt` INTEGER NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `accuracyMeters` REAL NOT NULL,
                `altitudeMeters` REAL,
                `speedMetersPerSecond` REAL NOT NULL,
                `acceleration` REAL,
                `leanDegrees` INTEGER,
                `segment` INTEGER NOT NULL,
                FOREIGN KEY(`rideId`) REFERENCES `rides`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_points_rideId` ON `ride_points` (`rideId`)")
    }
}
