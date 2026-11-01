package com.gwynn7.motolog

import android.content.Context
import android.net.Uri
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates a user-selected, self-contained backup without asking for broad storage access.
 * The archive contains both Room databases and the app-managed images.
 */
class LocalBackupManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun inspect(uri: Uri): BackupType = withContext(Dispatchers.IO) {
        openInput(uri).use { input ->
            val header = readHeader(input)
            when {
                header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> BackupType.ARCHIVE
                header.contentEquals(SQLITE_HEADER) -> BackupType.LEGACY_DATABASE
                else -> throw IllegalArgumentException("Unsupported backup file")
            }
        }
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        ensureDatabaseFilesExist()
        closeDatabases()

        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                writeTextEntry(zip, MANIFEST_ENTRY, JSONObject()
                    .put("format", ARCHIVE_FORMAT)
                    .put("version", ARCHIVE_VERSION)
                    .put("createdAt", System.currentTimeMillis())
                    .toString())
                writeFileEntry(zip, MOTORCYCLE_DATABASE_ENTRY, databaseFile(MOTORCYCLE_DATABASE))
                writeFileEntry(zip, GEAR_DATABASE_ENTRY, databaseFile(GEAR_DATABASE))
                imageDirectory().listFiles()
                    ?.filter(File::isFile)
                    ?.forEach { image -> writeFileEntry(zip, "$IMAGES_DIRECTORY/${image.name}", image) }
            }
        } ?: throw IllegalStateException("Could not create the backup file")
    }

    suspend fun restoreArchive(uri: Uri) = withContext(Dispatchers.IO) {
        val temporaryDirectory = createTemporaryDirectory()
        try {
            extractArchive(uri, temporaryDirectory)
            validateArchive(temporaryDirectory)
            closeDatabases()
            replaceDatabase(MOTORCYCLE_DATABASE, File(temporaryDirectory, MOTORCYCLE_DATABASE_ENTRY))
            replaceDatabase(GEAR_DATABASE, File(temporaryDirectory, GEAR_DATABASE_ENTRY))
            replaceImages(File(temporaryDirectory, IMAGES_DIRECTORY))
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    suspend fun restoreLegacyDatabase(uri: Uri, database: LegacyDatabase) = withContext(Dispatchers.IO) {
        val temporaryFile = File(appContext.cacheDir, "motolog-legacy-${UUID.randomUUID()}.db")
        try {
            openInput(uri).use { input -> copyWithLimit(input, temporaryFile) }
            require(isSqliteDatabase(temporaryFile)) { "The selected file is not a SQLite database" }
            closeDatabases()
            replaceDatabase(database.fileName, temporaryFile)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun ensureDatabaseFilesExist() {
        MotorcycleDatabase.getDatabase(appContext)
        GearDatabase.getDatabase(appContext)
    }

    private fun closeDatabases() {
        MotorcycleDatabase.closeDatabase()
        GearDatabase.closeDatabase()
    }

    private fun extractArchive(uri: Uri, destination: File) {
        val extractedEntries = mutableSetOf<String>()
        var extractedBytes = 0L
        openInput(uri).use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    require(isAllowedEntry(entry.name)) { "Unexpected backup entry" }
                    require(extractedEntries.add(entry.name)) { "Duplicate backup entry" }
                    val outputFile = safeOutputFile(destination, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var count = zip.read(buffer)
                            while (count != -1) {
                                extractedBytes += count
                                require(extractedBytes <= MAX_ARCHIVE_BYTES) { "Backup is too large" }
                                output.write(buffer, 0, count)
                                count = zip.read(buffer)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun validateArchive(directory: File) {
        val manifest = File(directory, MANIFEST_ENTRY)
        require(manifest.isFile) { "Backup manifest is missing" }
        val manifestData = JSONObject(manifest.readText())
        require(manifestData.optString("format") == ARCHIVE_FORMAT) { "Unsupported backup format" }
        require(manifestData.optInt("version") == ARCHIVE_VERSION) { "Unsupported backup version" }
        require(isSqliteDatabase(File(directory, MOTORCYCLE_DATABASE_ENTRY))) { "Motorcycle database is missing" }
        require(isSqliteDatabase(File(directory, GEAR_DATABASE_ENTRY))) { "Gear database is missing" }
    }

    private fun replaceDatabase(name: String, source: File) {
        val destination = databaseFile(name)
        destination.parentFile?.mkdirs()
        deleteDatabaseFiles(destination)
        source.copyTo(destination, overwrite = true)
    }

    private fun replaceImages(sourceDirectory: File) {
        val destinationDirectory = imageDirectory()
        destinationDirectory.deleteRecursively()
        if (sourceDirectory.isDirectory) {
            sourceDirectory.copyRecursively(destinationDirectory, overwrite = true)
        } else {
            destinationDirectory.mkdirs()
        }
    }

    private fun deleteDatabaseFiles(database: File) {
        listOf(database, File("${database.path}-wal"), File("${database.path}-shm"), File("${database.path}-journal"))
            .forEach { file -> if (file.exists()) file.delete() }
    }

    private fun writeTextEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, name: String, source: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(source).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun openInput(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected file")

    private fun copyWithLimit(input: InputStream, output: File) {
        var copiedBytes = 0L
        output.outputStream().buffered().use { destination ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count != -1) {
                copiedBytes += count
                require(copiedBytes <= MAX_LEGACY_DATABASE_BYTES) { "Database is too large" }
                destination.write(buffer, 0, count)
                count = input.read(buffer)
            }
        }
    }

    private fun isSqliteDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        return FileInputStream(file).use { input ->
            readHeader(input).contentEquals(SQLITE_HEADER)
        }
    }

    private fun readHeader(input: InputStream): ByteArray {
        val header = ByteArray(SQLITE_HEADER.size)
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            if (count == -1) return header.copyOf(offset)
            offset += count
        }
        return header
    }

    private fun createTemporaryDirectory(): File =
        File(appContext.cacheDir, "motolog-restore-${UUID.randomUUID()}").apply { mkdirs() }

    private fun databaseFile(name: String): File = appContext.getDatabasePath(name)
    private fun imageDirectory(): File = File(appContext.filesDir, IMAGES_DIRECTORY)

    private fun isAllowedEntry(name: String): Boolean =
        name == MANIFEST_ENTRY || name == MOTORCYCLE_DATABASE_ENTRY || name == GEAR_DATABASE_ENTRY ||
            name.startsWith("$IMAGES_DIRECTORY/")

    private fun safeOutputFile(root: File, entryName: String): File {
        val output = File(root, entryName).canonicalFile
        require(output.path.startsWith("${root.canonicalPath}${File.separator}")) { "Invalid backup entry" }
        return output
    }

    enum class BackupType {
        ARCHIVE,
        LEGACY_DATABASE
    }

    enum class LegacyDatabase(val fileName: String) {
        MOTORCYCLES(MOTORCYCLE_DATABASE),
        GEAR(GEAR_DATABASE)
    }

    private companion object {
        const val ARCHIVE_FORMAT = "motolog-backup"
        const val ARCHIVE_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val MOTORCYCLE_DATABASE = "motorcycles_db"
        const val GEAR_DATABASE = "gear_db"
        const val MOTORCYCLE_DATABASE_ENTRY = "databases/$MOTORCYCLE_DATABASE"
        const val GEAR_DATABASE_ENTRY = "databases/$GEAR_DATABASE"
        const val IMAGES_DIRECTORY = "pictures"
        const val MAX_ARCHIVE_BYTES = 200L * 1024 * 1024
        const val MAX_LEGACY_DATABASE_BYTES = 50L * 1024 * 1024
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
