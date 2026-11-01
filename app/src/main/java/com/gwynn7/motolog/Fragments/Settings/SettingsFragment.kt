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
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.LocalBackupManager
import com.gwynn7.motolog.LocaleHelper
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {
    private val backupManager by lazy { LocalBackupManager(requireContext()) }

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { uri -> uri?.let(::exportBackup) }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::confirmImport)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<ListPreference>("language")?.apply {
            value = LocaleHelper.getLanguage(requireContext()).value
            setOnPreferenceChangeListener { _, newValue ->
                val language = LocaleHelper.Language.entries.first { it.value == newValue }
                LocaleHelper.setLocale(requireContext(), language)
                restartActivity()
                true
            }
        }

        findPreference<ListPreference>("distance")?.apply {
            value = UnitHelper.distance.value
            setOnPreferenceChangeListener { _, newValue ->
                val distance = UnitHelper.Distance.entries.first { it.value == newValue }
                UnitHelper.saveDistance(requireContext(), lifecycleScope, distance)
                true
            }
        }

        findPreference<ListPreference>("currency")?.apply {
            value = UnitHelper.currency.value
            setOnPreferenceChangeListener { _, newValue ->
                val currency = UnitHelper.Currency.entries.first { it.value == newValue }
                UnitHelper.saveCurrency(requireContext(), lifecycleScope, currency)
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                    importBackup.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream"))
                    true
                }
                else -> false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            runCatching { backupManager.export(uri) }
                .onSuccess { showExportSuccess() }
                .onFailure { showBackupError(R.string.backup_export_error) }
        }
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
                .onFailure { showBackupError(R.string.backup_import_error) }
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

    private fun restoreArchive(uri: Uri) {
        lifecycleScope.launch {
            runCatching { backupManager.restoreArchive(uri) }
                .onSuccess { showImportSuccess() }
                .onFailure { showBackupError(R.string.backup_import_error) }
        }
    }

    private fun restoreLegacyDatabase(uri: Uri, target: LocalBackupManager.LegacyDatabase) {
        lifecycleScope.launch {
            runCatching { backupManager.restoreLegacyDatabase(uri, target) }
                .onSuccess { showImportSuccess() }
                .onFailure { showBackupError(R.string.backup_import_error) }
        }
    }

    private fun showExportSuccess() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_export_complete)
            .setMessage(R.string.restart_app)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { restartApp() }
            .show()
    }

    private fun showImportSuccess() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_import_complete)
            .setMessage(R.string.restart_app)
            .setPositiveButton(R.string.ok, null)
            .setOnDismissListener { restartApp() }
            .show()
    }

    private fun showBackupError(title: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(R.string.unknown_backup_import_error)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun defaultBackupName(): String =
        "motolog-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.motolog"

    private fun restartActivity() {
        val intent = requireActivity().intent
        requireActivity().finish()
        startActivity(intent)
    }

    private fun restartApp() {
        val context: Context = requireActivity().applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        MotorcycleDatabase.closeDatabase()
        GearDatabase.closeDatabase()
        context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
        requireActivity().finishAffinity()
    }

    private companion object {
        const val BACKUP_MIME_TYPE = "application/vnd.motolog.backup+zip"
    }
}
