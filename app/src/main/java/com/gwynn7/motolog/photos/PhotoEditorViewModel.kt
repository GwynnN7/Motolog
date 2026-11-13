package com.gwynn7.motolog.photos

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keep only private file references in saved state, never bitmaps or external gallery paths. */
class PhotoEditorViewModel(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    private val store = PhotoStore(application)
    val selection = state.getStateFlow("photoSelection", "keep")
    val busy = MutableStateFlow(false)
    val saved = MutableStateFlow<Boolean?>(null)
    val photoError = MutableStateFlow(false)
    val draft: Uri? get() = selection.value.takeUnless { it == "keep" || it == "remove" }?.let(Uri::parse)
    val removeImage get() = selection.value == "remove"

    init {
        val keep = setOfNotNull(state.get<String>("photoSelection"), state.get<String>("cropOutput"))
        viewModelScope.launch(Dispatchers.IO) { store.cleanOldDrafts(keep) }
    }

    fun prepareCrop(input: Uri): Uri {
        // Persist only while the crop activity needs this grant (including process recreation).
        try { getApplication<Application>().contentResolver.takePersistableUriPermission(input, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some document providers offer only a transient grant. */ }
        state["cropInput"] = input.toString()
        return store.newDraft().also { state["cropOutput"] = it.toString() }
    }

    fun completeCrop(success: Boolean, failed: Boolean) {
        val output = state.get<String>("cropOutput")?.let(Uri::parse)
        releaseInput()
        state.remove<String>("cropOutput")
        if (!success || output == null) {
            store.deleteDraft(output)
            if (failed) photoError.value = true
            return
        }
        busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.decode(requireNotNull(store.resolve(output, true)), 1200).recycle() }
                store.deleteDraft(draft)
                state["photoSelection"] = output.toString()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { store.deleteDraft(output); photoError.value = true }
            finally { busy.value = false }
        }
    }

    fun remove() {
        store.deleteDraft(draft)
        state["photoSelection"] = "remove"
    }

    fun save(operation: () -> Deferred<Result<Unit>>) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { saved.value = operation().await().isSuccess }
            finally { busy.value = false }
        }
    }

    private fun releaseInput() {
        val input = state.remove<String>("cropInput")?.let(Uri::parse) ?: return
        try { getApplication<Application>().contentResolver.releasePersistableUriPermission(input, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { }
    }

    override fun onCleared() {
        // Permanent navigation away, not a configuration change. Committed photos are separate files.
        releaseInput()
        store.deleteDraft(draft)
        store.deleteDraft(state.get<String>("cropOutput")?.let(Uri::parse))
    }
}
