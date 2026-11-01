package com.gwynn7.motolog.Database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog
import org.json.JSONArray

@Database(
    entities = [Motorcycle::class, DistanceLog::class, RepairsLog::class, ModsLog::class],
    version = 15,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MotorcycleDatabase : RoomDatabase() {
    abstract fun motorcycleDao(): MotorcycleDAO
    abstract fun distanceLogDao(): DistanceLogDAO
    abstract fun repairsLogDao(): RepairsLogDAO
    abstract fun modsLogDao(): ModsLogDAO

    companion object {
        @Volatile
        private var INSTANCE: MotorcycleDatabase? = null

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                /*
                 * Keep the old table available while the JSON columns are unpacked.  Creating
                 * the child tables before replacing `motorcycle` makes SQLite reject the drop
                 * when foreign-key enforcement is enabled.
                 */
                db.execSQL("ALTER TABLE `motorcycle` RENAME TO `motorcycle_legacy`")

                db.execSQL("""
                    CREATE TABLE `motorcycle` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `manufacturer` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `alias` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `start_km` INTEGER NOT NULL,
                        `personal_km` INTEGER NOT NULL,
                        `image` TEXT,
                        `listImage` TEXT,
                        `cylinders` INTEGER NOT NULL,
                        `engine_cc` REAL NOT NULL,
                        `horse_power` REAL NOT NULL,
                        `torque` REAL NOT NULL,
                        `price` REAL NOT NULL,
                        `front_tire` TEXT NOT NULL,
                        `rear_tire` TEXT NOT NULL,
                        `plate_number` TEXT NOT NULL,
                        `insurance` INTEGER NOT NULL,
                        `tax` INTEGER NOT NULL,
                        `inspection` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `motorcycle` (
                        id, manufacturer, model, alias, year, start_km, personal_km, image,
                        listImage, cylinders, engine_cc, horse_power, torque, price,
                        front_tire, rear_tire, plate_number, insurance, tax, inspection
                    )
                    SELECT
                        id, manufacturer, model, alias, year, start_km, personal_km, image,
                        listImage, cylinders, engine_cc, horse_power, torque, price,
                        front_tire, rear_tire, plate_number, insurance, tax, inspection
                    FROM `motorcycle_legacy`
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE `distance_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `motorcycleId` INTEGER NOT NULL,
                        `distance` INTEGER NOT NULL,
                        `date` INTEGER NOT NULL,
                        FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycle`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX `index_distance_log_motorcycleId` ON `distance_log` (`motorcycleId`)")

                db.execSQL("""
                    CREATE TABLE `repairs_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `motorcycleId` INTEGER NOT NULL,
                        `typeIndex` INTEGER NOT NULL,
                        `typeText` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `repair_km` INTEGER NOT NULL,
                        `price` REAL NOT NULL,
                        FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycle`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX `index_repairs_log_motorcycleId` ON `repairs_log` (`motorcycleId`)")

                db.execSQL("""
                    CREATE TABLE `mods_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `motorcycleId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `price` REAL NOT NULL,
                        FOREIGN KEY(`motorcycleId`) REFERENCES `motorcycle`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX `index_mods_log_motorcycleId` ON `mods_log` (`motorcycleId`)")

                db.query("SELECT id, distance, maintenance, mods FROM motorcycle_legacy").use { cursor ->
                    while (cursor.moveToNext()) {
                        val bikeId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                        migrateDistanceLogs(db, bikeId, cursor.getString(cursor.getColumnIndexOrThrow("distance")))
                        migrateRepairsLogs(db, bikeId, cursor.getString(cursor.getColumnIndexOrThrow("maintenance")))
                        migrateModsLogs(db, bikeId, cursor.getString(cursor.getColumnIndexOrThrow("mods")))
                    }
                }
                db.execSQL("DROP TABLE `motorcycle_legacy`")
            }

            private fun migrateDistanceLogs(db: SupportSQLiteDatabase, bikeId: Int, json: String?) {
                jsonArrayOrNull(json)?.let { logs ->
                    for (index in 0 until logs.length()) {
                        val log = logs.optJSONObject(index) ?: continue
                        if (!log.has("distance") || !log.has("date")) continue
                        db.execSQL(
                            "INSERT INTO distance_log (motorcycleId, distance, date) VALUES (?, ?, ?)",
                            arrayOf(bikeId, log.optInt("distance"), log.optLong("date"))
                        )
                    }
                }
            }

            private fun migrateRepairsLogs(db: SupportSQLiteDatabase, bikeId: Int, json: String?) {
                jsonArrayOrNull(json)?.let { logs ->
                    for (index in 0 until logs.length()) {
                        val log = logs.optJSONObject(index) ?: continue
                        if (!log.has("typeIndex") || !log.has("typeText") || !log.has("notes") ||
                            !log.has("date") || !log.has("repair_km") || !log.has("price")
                        ) continue
                        db.execSQL(
                            "INSERT INTO repairs_log (motorcycleId, typeIndex, typeText, notes, date, repair_km, price) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(
                                bikeId,
                                log.optInt("typeIndex"),
                                log.optString("typeText"),
                                log.optString("notes"),
                                log.optLong("date"),
                                log.optInt("repair_km"),
                                log.optDouble("price")
                            )
                        )
                    }
                }
            }

            private fun migrateModsLogs(db: SupportSQLiteDatabase, bikeId: Int, json: String?) {
                jsonArrayOrNull(json)?.let { logs ->
                    for (index in 0 until logs.length()) {
                        val log = logs.optJSONObject(index) ?: continue
                        if (!log.has("title") || !log.has("description") || !log.has("date") || !log.has("price")) continue
                        db.execSQL(
                            "INSERT INTO mods_log (motorcycleId, title, description, date, price) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(
                                bikeId,
                                log.optString("title"),
                                log.optString("description"),
                                log.optLong("date"),
                                log.optDouble("price")
                            )
                        )
                    }
                }
            }

            private fun jsonArrayOrNull(json: String?): JSONArray? = try {
                json?.takeIf { it.isNotBlank() && it != "[]" }?.let(::JSONArray)
            } catch (_: Exception) {
                null
            }
        }

        fun getDatabase(context: Context): MotorcycleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MotorcycleDatabase::class.java,
                    "motorcycles_db"
                )
                    .addMigrations(MIGRATION_14_15)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun closeDatabase() = synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
