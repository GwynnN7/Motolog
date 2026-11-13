package com.gwynn7.motolog.backup

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Recoverable multi-file replace. A crash before COMMITTED restores every original file. */
class RestoreJournal(context: Context) {
    private val app = context.applicationContext
    private val journal = File(app.noBackupFilesDir, "motolog-restore-journal")
    private val cleanup = File(app.noBackupFilesDir, "motolog-restore-cleanup")
    private val metadata get() = File(journal, "manifest.json")

    fun hasPendingRestore(): Boolean = journal.exists() || cleanup.exists()

    suspend fun recover() {
        check(cleanup.deleteRecursively()) { "Could not clean up completed restore" }
        if (!metadata.isFile) { check(journal.deleteRecursively()); return }
        val manifest = JSONObject(metadata.readText())
        if (!File(journal, "COMMITTED").exists()) {
            val entries = manifest.getJSONArray("entries")
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val target = target(entry.getString("name"))
                val original = File(journal, "old/$index")
                if (original.exists()) {
                    delete(target)
                    target.parentFile?.mkdirs()
                    check(original.renameTo(target)) { "Could not recover original data" }
                } else if (!entry.getBoolean("existed")) delete(target)
            }
            BackupSettings.restore(app, manifest.getJSONObject("oldSettings"))
        }
        // Rename first: a crash while deleting COMMITTED and old files must never look
        // like an unfinished restore on the next launch.
        check(journal.renameTo(cleanup)) { "Could not finalize restore recovery" }
        check(cleanup.deleteRecursively()) { "Could not clean up completed restore" }
    }

    // Once replacement begins, finish or roll back even if its screen is destroyed.
    suspend fun replace(replacements: Map<String, File>, settings: JSONObject? = null) = withContext(NonCancellable) {
        recover()
        File(journal, "old").mkdirs()
        val entries = JSONArray()
        replacements.keys.forEach { name ->
            entries.put(JSONObject().put("name", name).put("existed", target(name).exists()))
        }
        atomicWrite(metadata, JSONObject().put("entries", entries).put("oldSettings", BackupSettings.capture(app)).toString())
        try {
            replacements.entries.forEachIndexed { index, (name, staged) ->
                val destination = target(name)
                destination.parentFile?.mkdirs()
                if (destination.exists()) check(destination.renameTo(File(journal, "old/$index"))) { "Could not preserve existing data" }
                check(staged.renameTo(destination)) { "Could not install restored data" }
            }
            settings?.let { BackupSettings.restore(app, it) }
            atomicWrite(File(journal, "COMMITTED"), "complete")
        } catch (failure: Exception) {
            recover()
            throw failure
        }
        recover()
    }

    private fun target(name: String): File = when (name) {
        "pictures" -> File(app.filesDir, "pictures")
        "motorcycles_db", "gear_db" -> app.getDatabasePath(name)
        else -> error("Unexpected restore target")
    }

    private fun delete(file: File) {
        if (file.exists()) check(file.deleteRecursively()) { "Could not remove restore target" }
    }

    private fun atomicWrite(file: File, text: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}
