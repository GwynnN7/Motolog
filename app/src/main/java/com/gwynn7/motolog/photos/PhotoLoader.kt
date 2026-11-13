package com.gwynn7.motolog.photos

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gwynn7.motolog.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bounded background decoding; each recycled row cancels its previous request. */
object PhotoLoader {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun clear(view: ImageView) {
        (view.getTag(R.id.photo_load_attach) as? View.OnAttachStateChangeListener)?.let { view.removeOnAttachStateChangeListener(it) }
        view.setTag(R.id.photo_load_attach, null)
        (view.getTag(R.id.photo_load_job) as? Job)?.cancel()
        view.setTag(R.id.photo_load_job, null)
        view.setImageDrawable(null)
    }

    fun load(view: ImageView, uri: Uri?, placeholder: Int, limit: Int = 1200) {
        clear(view)
        view.setImageResource(placeholder)
        if (uri == null) return
        val owner = view.findViewTreeLifecycleOwner()
        if (owner == null) {
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = load(view, uri, placeholder, limit)
                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            view.setTag(R.id.photo_load_attach, listener)
            view.addOnAttachStateChangeListener(listener)
            return
        }
        val store = PhotoStore(view.context)
        view.setTag(R.id.photo_load_job, owner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val file = store.resolve(uri, allowDraft = true)?.takeIf { it.isFile } ?: return@withContext null
                    val key = "${file.canonicalPath}:${file.lastModified()}:${file.length()}:$limit"
                    cache.get(key) ?: store.decode(file, limit).also { cache.put(key, it) }
                } catch (_: Exception) { null }
            }
            if (bitmap != null) view.setImageBitmap(bitmap)
        })
    }
}
