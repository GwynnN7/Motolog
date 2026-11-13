package com.gwynn7.motolog.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** The same archive contract is used for writing and restoring portable backups. */
internal object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val MOTORCYCLE_ENTRY = "databases/motorcycles_db"
    const val GEAR_ENTRY = "databases/gear_db"
    const val MAX_BYTES = 512L * 1024 * 1024
    val databaseEntries = listOf(MOTORCYCLE_ENTRY, GEAR_ENTRY)

    fun isPhotoName(name: String): Boolean = name.isNotEmpty() && name !in listOf(".", "..") &&
        name.none { it == '/' || it == '\\' || it.isISOControl() }

    private fun allowed(name: String, directory: Boolean): Boolean {
        if (directory) return name == "databases/" || name == "pictures/"
        return name == MANIFEST || databaseEntries.any { database ->
            name == database || listOf("-wal", "-shm", "-journal").any { name == database + it }
        } || (name.startsWith("pictures/") && isPhotoName(name.removePrefix("pictures/")))
    }

    fun extract(source: InputStream, stage: File) {
        val names = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(source.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val diagnostic = name.take(160).map { if (it.isISOControl()) '?' else it }.joinToString("")
                require(names.add(name) && names.size <= 20000) { "Duplicate or excessive backup entries: $diagnostic" }
                require(allowed(name, entry.isDirectory)) { "Unexpected backup entry: $diagnostic" }
                val target = File(stage, name).canonicalFile
                require(target.path.startsWith(stage.canonicalPath + File.separator)) { "Invalid backup path: $diagnostic" }
                if (entry.isDirectory) {
                    check(target.isDirectory || target.mkdirs()) { "Could not stage backup folder" }
                    // Directory entries carry no payload, even in archives created by ZIP utilities.
                    require(zip.read() == -1) { "Unexpected directory content: $diagnostic" }
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { destination ->
                        var entryBytes = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            entryBytes += count
                            require(name != MANIFEST || entryBytes <= 65536) { "Backup manifest is too large" }
                            require(total <= MAX_BYTES) { "Backup is too large" }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
