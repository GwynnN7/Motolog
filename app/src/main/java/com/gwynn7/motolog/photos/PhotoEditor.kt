package com.gwynn7.motolog.photos

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.gwynn7.motolog.R
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** Shared permission-free picker/crop flow for bike and gear editors. Register during creation. */
class PhotoEditor(private val fragment: Fragment, private val model: () -> PhotoEditorViewModel) {
    private val crop = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model().completeCrop(result.resultCode == Activity.RESULT_OK, result.resultCode == Activity.RESULT_FIRST_USER)
    }
    private val pick = fragment.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val output = model().prepareCrop(uri)
                crop.launch(Intent(fragment.requireContext(), PhotoCropActivity::class.java)
                    .setData(uri).putExtra(PhotoCropActivity.OUTPUT, output.toString()))
            } catch (_: Exception) { model().completeCrop(success = false, failed = true) }
        }
    }

    val draft get() = model().draft
    val removeImage get() = model().removeImage
    fun save(operation: () -> Deferred<Result<Unit>>) = model().save(operation)

    fun bind(view: ImageView, original: Uri?, onSaved: () -> Unit) {
        view.contentDescription = fragment.getString(R.string.photo_edit)
        view.setOnClickListener {
            if (!model().busy.value) MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.photo_edit)
                .setItems(arrayOf(fragment.getString(R.string.photo_choose), fragment.getString(R.string.photo_remove))) { _, choice ->
                    if (choice == 0) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else model().remove()
                }.show()
        }
        view.setOnLongClickListener { if (!model().busy.value) model().remove(); true }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { model().selection.collect {
                    val selected = if (model().removeImage) null else model().draft ?: original
                    view.maxHeight = ((if (selected == null) 128 else 360) * view.resources.displayMetrics.density).toInt()
                    PhotoLoader.load(view, selected, R.drawable.add_photo)
                } }
                launch {
                    val progress = MaterialAlertDialogBuilder(fragment.requireContext())
                        .setMessage(R.string.saving_changes).setCancelable(false).create()
                    try { model().busy.collect { busy ->
                        view.isEnabled = !busy
                        if (busy) progress.show() else progress.dismiss()
                    } } finally { progress.dismiss() }
                }
                launch { model().photoError.collect { if (it) {
                    Snackbar.make(view, R.string.photo_failed, Snackbar.LENGTH_LONG).show()
                    model().photoError.value = false
                } } }
                launch { model().saved.collect { success -> if (success != null) {
                    model().saved.value = null
                    if (success) onSaved() else MaterialAlertDialogBuilder(fragment.requireContext())
                        .setMessage(R.string.save_changes_failed).setPositiveButton(R.string.ok, null).show()
                } } }
            }
        }
    }
}
