package com.gwynn7.motolog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Room
import com.gwynn7.motolog.Database.DataAccess
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.Database.snapshotTo
import com.gwynn7.motolog.backup.BackupArchive
import com.gwynn7.motolog.backup.BackupArchive.MANIFEST
import com.gwynn7.motolog.backup.BackupArchive.MOTORCYCLE_ENTRY
import com.gwynn7.motolog.backup.BackupArchive.GEAR_ENTRY
import com.gwynn7.motolog.backup.BackupArchive.MAX_BYTES
import com.gwynn7.motolog.backup.BackupSettings
import com.gwynn7.motolog.backup.RestoreJournal
import com.gwynn7.motolog.rides.data.MIGRATION_15_16
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable databases + photos + settings. Version 1 archives and published SQLite exports still import. */
class LocalBackupManager(context: Context) {
    private val app = context.applicationContext
    var needsRestart = false
        private set

    suspend fun inspect(uri: Uri): BackupType = withContext(Dispatchers.IO) {
        input(uri).use { stream ->
            val header = stream.readHeader()
            when {
                header.size >= 2 && header[0] == 80.toByte() && header[1] == 75.toByte() -> BackupType.ARCHIVE
                header.contentEquals(SQLITE_HEADER) -> BackupType.LEGACY_DATABASE
                else -> error("Unsupported backup file")
            }
        }
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) { DataAccess.write {
        requireNoActiveRide()
        val stage = temporaryDirectory()
        try {
            MotorcycleDatabase.getDatabase(app).snapshotTo(File(stage, MOTORCYCLE_ENTRY))
            GearDatabase.getDatabase(app).snapshotTo(File(stage, GEAR_ENTRY))
            val manifest = JSONObject().put("format", FORMAT).put("version", 2)
                .put("createdAt", System.currentTimeMillis()).put("settings", BackupSettings.capture(app))
            File(stage, MANIFEST).writeText(manifest.toString())
            // Snapshots are self-contained. Never include incidental SQLite files from staging.
            val files = (listOf(MANIFEST) + BackupArchive.databaseEntries)
                .map { File(stage, it) to it }.toMutableList()
            pictures().listFiles()?.filter { it.isFile }?.forEach {
                require(BackupArchive.isPhotoName(it.name)) { "Unsupported photo filename: ${it.name}" }
                files += it to "pictures/${it.name}"
            }
            require(files.sumOf { it.first.length() } <= MAX_BYTES) { "Backup is too large" }
            val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error("Could not create backup")
            ZipOutputStream(stream.buffered()).use { zip ->
                for ((file, name) in files) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally { stage.deleteRecursively() }
    } }

    suspend fun restoreArchive(uri: Uri) = withContext(Dispatchers.IO) { DataAccess.write {
        requireNoActiveRide()
        val stage = temporaryDirectory()
        var phase = "reading archive"
        try {
            input(uri).use { BackupArchive.extract(it, stage) }
            val manifest = JSONObject(File(stage, MANIFEST).readText())
            require(manifest.getString("format") == FORMAT && manifest.getInt("version") in 1..2) { "Unsupported backup version" }
            phase = "checking settings"
            val settings = manifest.optJSONObject("settings")?.also(BackupSettings::validate)
            val bikes = File(stage, MOTORCYCLE_ENTRY)
            val gear = File(stage, GEAR_ENTRY)
            phase = "checking motorcycle database"
            validateDatabase(bikes, LegacyDatabase.MOTORCYCLES)
            phase = "checking gear database"
            validateDatabase(gear, LegacyDatabase.GEAR)
            phase = "resolving photo paths"
            val photos = File(stage, "pictures").apply { mkdirs() }
            rebasePhotos(bikes, "motorcycle", photos)
            rebasePhotos(gear, "gear", photos)
            phase = "closing live databases"
            closeForRestore()
            phase = "installing restored files and settings"
            RestoreJournal(app).replace(linkedMapOf(MOTORCYCLES to bikes, GEAR to gear, "pictures" to photos), settings)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw java.io.IOException("Import failed while $phase: ${failure.message ?: failure.javaClass.simpleName}", failure)
        } finally { stage.deleteRecursively() }
    } }

    suspend fun restoreLegacyDatabase(uri: Uri, database: LegacyDatabase) = withContext(Dispatchers.IO) { DataAccess.write {
        requireNoActiveRide()
        val stage = temporaryDirectory()
        try {
            val file = File(stage, database.fileName)
            input(uri).use { source -> file.outputStream().use { destination ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Backup is too large" }
                    destination.write(buffer, 0, count)
                }
            } }
            validateDatabase(file, database)
            rebasePhotos(file, if (database == LegacyDatabase.MOTORCYCLES) "motorcycle" else "gear", pictures())
            closeForRestore()
            RestoreJournal(app).replace(mapOf(database.fileName to file))
        } finally { stage.deleteRecursively() }
    } }

    private suspend fun requireNoActiveRide() {
        check(MotorcycleDatabase.getDatabase(app).rideDao().activeRide() == null) { app.getString(R.string.ride_backup_active) }
    }

    /** All schema checks and migrations happen in staging, before any live data is replaced. */
    private fun validateDatabase(file: File, database: LegacyDatabase) {
        require(file.isFile && file.inputStream().use { it.readHeader().contentEquals(SQLITE_HEADER) }) { "Missing or invalid database" }
        // Older exports may include SQLite journals. Let SQLite recover committed WAL data
        // or roll back a hot journal here, in staging. Rebuild its transient shared-memory index.
        val sharedMemory = File(file.path + "-shm")
        check(!sharedMemory.exists() || sharedMemory.delete()) { "Could not reset staged database index" }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.rawQuery("PRAGMA quick_check", null).use { check -> require(check.moveToFirst() && check.getString(0) == "ok") { "Damaged database" } }
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('trigger','view')", null).use { require(!it.moveToFirst()) { "Unsupported database objects" } }
        }
        val room = when (database) {
            LegacyDatabase.MOTORCYCLES -> Room.databaseBuilder(app, MotorcycleDatabase::class.java, file.path)
                .addMigrations(MotorcycleDatabase.MIGRATION_14_15, MIGRATION_15_16).build()
            LegacyDatabase.GEAR -> Room.databaseBuilder(app, GearDatabase::class.java, file.path).build()
        }
        try {
            val sqlite = room.openHelper.writableDatabase
            sqlite.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Database has broken references" } }
            sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { require(it.moveToFirst() && it.getInt(0) == 0) }
        } finally { room.close() }
    }

    private fun rebasePhotos(file: File, table: String, photoSource: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.beginTransaction()
            try {
                for (column in listOf("image", "listImage")) {
                    sqlite.rawQuery("SELECT id, $column FROM $table WHERE $column IS NOT NULL", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val uri = Uri.parse(cursor.getString(1))
                            if (uri.scheme != "file" || uri.path?.contains("/pictures/") != true) continue
                            val name = uri.lastPathSegment ?: continue
                            if (!BackupArchive.isPhotoName(name) || !File(photoSource, name).isFile) continue
                            sqlite.execSQL("UPDATE $table SET $column = ? WHERE id = ?", arrayOf<Any>(File(pictures(), name).toUri().toString(), cursor.getInt(0)))
                        }
                    }
                }
                sqlite.setTransactionSuccessful()
            } finally { sqlite.endTransaction() }
        }
    }

    private fun closeForRestore() {
        val databases = listOf(MotorcycleDatabase.getDatabase(app), GearDatabase.getDatabase(app))
        databases.forEach { database ->
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database is busy; try again" }
            }
        }
        MotorcycleDatabase.closeDatabase()
        GearDatabase.closeDatabase()
        needsRestart = true
        for (name in listOf(MOTORCYCLES, GEAR)) for (suffix in listOf("-wal", "-shm", "-journal")) {
            val file = File(app.getDatabasePath(name).path + suffix)
            if (file.exists()) check(file.delete()) { "Could not close database" }
        }
    }

    private fun input(uri: Uri): InputStream = app.contentResolver.openInputStream(uri) ?: error("Could not open backup")
    private fun pictures() = File(app.filesDir, "pictures")
    private fun temporaryDirectory() = File(app.cacheDir, "motolog-backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
    private fun InputStream.readHeader(): ByteArray {
        val header = ByteArray(SQLITE_HEADER.size)
        var offset = 0
        while (offset < header.size) {
            val count = read(header, offset, header.size - offset)
            if (count < 0) return header.copyOf(offset)
            offset += count
        }
        return header
    }

    enum class BackupType { ARCHIVE, LEGACY_DATABASE }
    enum class LegacyDatabase(val fileName: String) { MOTORCYCLES("motorcycles_db"), GEAR("gear_db") }
    private companion object {
        const val FORMAT = "motolog-backup"
        const val MOTORCYCLES = "motorcycles_db"
        const val GEAR = "gear_db"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
