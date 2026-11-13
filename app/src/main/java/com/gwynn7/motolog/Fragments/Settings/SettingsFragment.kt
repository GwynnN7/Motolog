package com.gwynn7.motolog.Fragments.Settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.viewModels
import com.gwynn7.motolog.backup.BackupViewModel
import com.gwynn7.motolog.backup.BackupState
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gwynn7.motolog.settings.AppSettings
import com.gwynn7.motolog.settings.settings
import kotlinx.coroutines.flow.first
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.LocalBackupManager
import com.gwynn7.motolog.LocaleHelper
import com.gwynn7.motolog.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {
    private val backupManager by lazy { LocalBackupManager(requireContext()) }
    private val backups: BackupViewModel by viewModels()
    private var backupDialog: androidx.appcompat.app.AlertDialog? = null

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let(::exportBackup) }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::confirmImport)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val context = requireContext().applicationContext
        preferenceScreen.isEnabled = false
        lifecycleScope.launch {
            val stored = context.settings.data.first()
            val tracking = AppSettings(context).tracking.first()
            findPreference<ListPreference>("language")?.apply {
                isPersistent = false
                value = LocaleHelper.getLanguage(context).value
                setOnPreferenceChangeListener { _, newValue ->
                    LocaleHelper.setLocale(context, LocaleHelper.Language.entries.first { it.value == newValue })
                    requireActivity().recreate()
                    true
                }
            }
            findPreference<ListPreference>("distance")?.apply {
                isPersistent = false
                value = stored[stringPreferencesKey("distance")] ?: "km"
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        context.settings.edit { it[stringPreferencesKey("distance")] = newValue as String }
                        value = newValue as String
                    }
                    false
                }
            }
            findPreference<ListPreference>("currency")?.apply {
                isPersistent = false
                value = stored[stringPreferencesKey("currency")] ?: "€"
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch {
                        context.settings.edit { it[stringPreferencesKey("currency")] = newValue as String }
                        value = newValue as String
                    }
                    false
                }
            }
            findPreference<ListPreference>("tracking_interval_ms")?.apply {
                isPersistent = false
                value = tracking.intervalMillis.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { AppSettings(context).trackingInterval((newValue as String).toLong()); value = newValue }
                    false
                }
            }
            findPreference<SwitchPreferenceCompat>("tracking_lean")?.apply {
                isPersistent = false
                isChecked = tracking.leanEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    lifecycleScope.launch { AppSettings(context).trackLean(newValue as Boolean); isChecked = newValue }
                    false
                }
            }
            preferenceScreen.isEnabled = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                backups.state.collect(::showBackupState)
            }
        }
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.export_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.menu_export -> {
                    exportBackup.launch(defaultBackupName())
                    true
                }
                R.id.menu_import -> {
                    importBackup.launch(arrayOf(BACKUP_MIME_TYPE, "application/zip", "application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3"))
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun exportBackup(uri: Uri) { backups.export(uri) }

    private fun showBackupState(state: BackupState) {
        backupDialog?.dismiss()
        backupDialog = null
        if (state.busy) {
            backupDialog = MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.backup_working)
                .setCancelable(false).show()
        } else if (state.title != null) {
            val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(state.title).setCancelable(false)
            if (state.error != null) builder.setMessage(state.error)
            else builder.setMessage(if (state.needsRestart) R.string.restart_app else R.string.backup_ready_no_restart)
            backupDialog = builder.setPositiveButton(R.string.ok) { _, _ ->
                backups.acknowledge()
                if (state.needsRestart) restartApp()
            }.show()
        }
    }

    override fun onDestroyView() {
        backupDialog?.dismiss()
        backupDialog = null
        super.onDestroyView()
    }

    private fun confirmImport(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_import)
            .setMessage(R.string.overwrite_data)
            .setNegativeButton(R.string.back, null)
            .setPositiveButton(R.string.import_data) { _, _ -> inspectAndImport(uri) }
            .show()
    }

    private fun inspectAndImport(uri: Uri) {
        lifecycleScope.launch {
            runCatching { backupManager.inspect(uri) }
                .onSuccess { type ->
                    when (type) {
                        LocalBackupManager.BackupType.ARCHIVE -> restoreArchive(uri)
                        LocalBackupManager.BackupType.LEGACY_DATABASE -> chooseLegacyDatabase(uri)
                    }
                }
                .onFailure { failure ->
                    android.util.Log.e("MotoLogBackup", "Could not inspect selected backup", failure)
                    showBackupError(R.string.backup_import_error, failure.message)
                }
        }
    }

    private fun chooseLegacyDatabase(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_import)
            .setSingleChoiceItems(
                arrayOf(getString(R.string.motorcycles), getString(R.string.gear)),
                0
            ) { dialog, which ->
                dialog.dismiss()
                val target = if (which == 0) {
                    LocalBackupManager.LegacyDatabase.MOTORCYCLES
                } else {
                    LocalBackupManager.LegacyDatabase.GEAR
                }
                restoreLegacyDatabase(uri, target)
            }
            .show()
    }

    private fun restoreArchive(uri: Uri) { backups.restoreArchive(uri) }

    private fun restoreLegacyDatabase(uri: Uri, target: LocalBackupManager.LegacyDatabase) {
        backups.restoreLegacy(uri, target)
    }

    private fun showBackupError(title: Int, details: String? = null) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title)
            .setMessage(details ?: getString(R.string.unknown_backup_import_error)).setPositiveButton(R.string.ok, null).show()
    }

    private fun defaultBackupName(): String =
        "motolog-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.motolog"

    private fun restartApp() {
        val context: Context = requireActivity().applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        MotorcycleDatabase.closeDatabase()
        GearDatabase.closeDatabase()
        requireActivity().finish()
        context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
    }

    private companion object {
        const val BACKUP_MIME_TYPE = "application/vnd.motolog.backup+zip"
    }
}
