package com.gwynn7.motolog.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwynn7.motolog.LocalBackupManager
import com.gwynn7.motolog.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupState(val busy: Boolean = false, val title: Int? = null,
    val error: String? = null, val needsRestart: Boolean = false)

/** Survives configuration changes without cancelling an import or losing its result. */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = LocalBackupManager(application)
    private val mutableState = MutableStateFlow(BackupState())
    val state = mutableState.asStateFlow()

    fun export(uri: Uri) = perform(false) { manager.export(uri) }
    fun restoreArchive(uri: Uri) = perform(true) { manager.restoreArchive(uri) }
    fun restoreLegacy(uri: Uri, target: LocalBackupManager.LegacyDatabase) =
        perform(true) { manager.restoreLegacyDatabase(uri, target) }

    fun acknowledge() { if (!state.value.busy) mutableState.value = BackupState() }

    private fun perform(importing: Boolean, action: suspend () -> Unit) {
        if (state.value.busy || state.value.title != null) return
        mutableState.value = BackupState(busy = true)
        viewModelScope.launch {
            try {
                action()
                mutableState.value = BackupState(title = if (importing) R.string.backup_import_complete else R.string.backup_export_complete,
                    needsRestart = manager.needsRestart)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.e("MotoLogBackup", "Backup operation failed (importing=$importing)", failure)
                mutableState.value = BackupState(title = if (importing) R.string.backup_import_error else R.string.backup_export_error,
                    error = failure.message ?: getApplication<Application>().getString(R.string.unknown_backup_import_error),
                    needsRestart = manager.needsRestart)
            }
        }
    }
}
