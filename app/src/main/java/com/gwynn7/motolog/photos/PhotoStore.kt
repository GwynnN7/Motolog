package com.gwynn7.motolog.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.net.toUri
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Call on IO, under DataAccess for committed photo/database changes. */
class PhotoStore(context: Context) {
    private val app = context.applicationContext
    private val pictures = File(app.filesDir, "pictures")
    private val drafts = File(app.noBackupFilesDir, "photo-drafts")

    fun resolve(uri: Uri?, allowDraft: Boolean = false): File? {
        if (uri?.scheme != "file") return null
        val path = uri.path ?: return null
        return PhotoFiles.resolve(pictures, path)
            ?: if (allowDraft) PhotoFiles.resolve(drafts, path) else null
    }

    fun newDraft(): Uri {
        check(drafts.isDirectory || drafts.mkdirs())
        return File(drafts, "${UUID.randomUUID()}.jpg").toUri()
    }

    fun deleteDraft(uri: Uri?) {
        val path = uri?.takeIf { it.scheme == "file" }?.path ?: return
        PhotoFiles.resolve(drafts, path)?.delete()
    }

    fun promoteDraft(from: Uri, to: Uri) {
        val source = requireNotNull(from.path?.let { PhotoFiles.resolve(drafts, it) })
        val target = requireNotNull(to.path?.let { PhotoFiles.resolve(drafts, it) })
        check(source.renameTo(target)) { "Could not finish cropped photo" }
    }

    fun cleanOldDrafts(keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        drafts.listFiles()?.filter { it.isFile && it.lastModified() < cutoff && it.toUri().toString() !in keep }
            ?.forEach { deleteDraft(it.toUri()) }
    }

    fun decode(file: File, limit: Int): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val (width, height) = PhotoFiles.dimensions(info.size.width, info.size.height, limit)
        decoder.setTargetSize(width, height)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    /** Files precede the DB commit; failed commits remove both new files, never the old photo. */
    suspend fun <T> commit(draft: Uri?, block: suspend (Pair<Uri, Uri>?) -> T): T = withContext(NonCancellable) {
        if (draft == null) return@withContext block(null)
        val source = requireNotNull(resolve(draft, allowDraft = true)) { "Photo draft is unavailable" }
        val created = mutableListOf<File>()
        val bitmap = decode(source, 1200)
        try {
            check(pictures.isDirectory || pictures.mkdirs())
            fun write(limit: Int): Uri {
                val file = File(pictures, "${UUID.randomUUID()}.jpg").also { created += it }
                val (width, height) = PhotoFiles.dimensions(bitmap.width, bitmap.height, limit)
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                try {
                    FileOutputStream(file).use {
                        check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, it))
                        it.fd.sync()
                    }
                } finally { if (scaled !== bitmap) scaled.recycle() }
                return file.toUri()
            }
            block(write(1200) to write(400))
        } catch (failure: Exception) {
            created.forEach { it.delete() }
            throw failure
        } finally { bitmap.recycle() }
    }

    /** Also protects imported archives that share one photo between several bikes/gear entries. */
    suspend fun deleteUnreferenced(vararg uris: Uri?) {
        try {
            val references = MotorcycleDatabase.getDatabase(app).motorcycleDao().photoUris() +
                GearDatabase.getDatabase(app).gearDao().photoUris()
            val used = references.mapNotNull { resolve(Uri.parse(it))?.canonicalPath }.toSet()
            uris.mapNotNull { resolve(it) }.filter { it.canonicalPath !in used }.forEach { it.delete() }
        } catch (_: Exception) {
            // A cleanup failure must not turn a successful database commit into a failed save.
        }
    }
}
