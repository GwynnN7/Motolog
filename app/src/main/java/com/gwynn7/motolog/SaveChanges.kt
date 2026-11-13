package com.gwynn7.motolog

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** Keep the editor alive until its write completes. Never report success before a commit. */
fun Fragment.saveChanges(operation: Deferred<Result<Unit>>, onSuccess: () -> Unit) {
    val dialog = MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.saving_changes)
        .setCancelable(false).create()
    dialog.show()
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            val result = operation.await()
            dialog.dismiss()
            if (result.isSuccess) onSuccess()
            else MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.save_changes_failed)
                .setPositiveButton(R.string.ok, null).show()
        } finally { dialog.dismiss() }
    }
}
