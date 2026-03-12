package com.gwynn7.motolog.Fragments.Settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gwynn7.motolog.Database.GearDatabase
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.LocaleHelper
import com.gwynn7.motolog.MainActivity
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR_BY_USER_CANCELED
import de.raphaelebner.roomdatabasebackup.core.OnCompleteListener.Companion.EXIT_CODE_ERROR_STORAGE_PERMISSONS_NOT_GRANTED
import de.raphaelebner.roomdatabasebackup.core.RoomBackup

class SettingsFragment : PreferenceFragmentCompat() {

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
                UnitHelper.saveDistance(requireContext(), distance)
                true
            }
        }

        findPreference<ListPreference>("currency")?.apply {
            value = UnitHelper.currency.value
            setOnPreferenceChangeListener { _, newValue ->
                val currency = UnitHelper.Currency.entries.first { it.value == newValue }
                UnitHelper.saveCurrency(requireContext(), currency)
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

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val storageItems = arrayOf(getString(R.string.motorcycles), getString(R.string.gear))
                val backup = (activity as MainActivity).backup
                    .database(MotorcycleDatabase.getDatabase(requireContext()))
                    .backupLocation(RoomBackup.BACKUP_FILE_LOCATION_CUSTOM_DIALOG)

                val alert = MaterialAlertDialogBuilder(requireContext())
                    .setNegativeButton(R.string.back, null)
                    .setSingleChoiceItems(storageItems, 0) { _, which ->
                        when (which) {
                            0 -> backup.database(MotorcycleDatabase.getDatabase(requireContext()))
                            1 -> backup.database(GearDatabase.getDatabase(requireContext()))
                        }
                    }

                when (menuItem.itemId) {
                    R.id.menu_export -> {
                        backup.apply {
                            onCompleteListener { success, _, exitCode ->
                                if (success) {
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.backup_export_complete))
                                        .setMessage(getString(R.string.image_not_included))
                                        .setPositiveButton(R.string.ok, null)
                                        .setOnDismissListener { restartApp() }
                                        .show()
                                } else {
                                    val alertBackup = MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.backup_export_error))
                                        .setPositiveButton(R.string.ok, null)
                                    if (exitCode == EXIT_CODE_ERROR_STORAGE_PERMISSONS_NOT_GRANTED) {
                                        alertBackup
                                            .setMessage(getString(R.string.check_permissions))
                                            .show()
                                    } else if (exitCode != EXIT_CODE_ERROR_BY_USER_CANCELED) {
                                        alertBackup
                                            .setMessage(getString(R.string.unknown_backup_export_error))
                                            .show()
                                    }
                                }
                            }
                        }
                        alert
                            .setTitle(R.string.choose_export)
                            .setPositiveButton(R.string.export_data) { _, _ ->
                                backup.backup()
                            }
                            .show()
                        return true
                    }
                    R.id.menu_import -> {
                        backup.apply {
                            onCompleteListener { success, _, exitCode ->
                                if (success) {
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.backup_import_complete))
                                        .setMessage(getString(R.string.restart_app))
                                        .setPositiveButton(R.string.ok, null)
                                        .setOnDismissListener { restartApp() }
                                        .show()
                                } else {
                                    val alertBackup = MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(getString(R.string.backup_import_error))
                                        .setPositiveButton(R.string.ok, null)
                                    if (exitCode == EXIT_CODE_ERROR_STORAGE_PERMISSONS_NOT_GRANTED) {
                                        alertBackup
                                            .setMessage(getString(R.string.check_permissions))
                                            .show()
                                    } else if (exitCode != EXIT_CODE_ERROR_BY_USER_CANCELED) {
                                        alertBackup
                                            .setMessage(getString(R.string.unknown_backup_import_error))
                                            .show()
                                    }
                                }
                            }
                        }

                        alert
                            .setTitle(R.string.choose_import)
                            .setPositiveButton(R.string.import_data) { _, _ ->
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.confirm_import)
                                    .setMessage(R.string.overwrite_data)
                                    .setPositiveButton(R.string.import_data) { _, _ ->
                                        backup.restore()
                                    }
                                    .setNegativeButton(R.string.back, null)
                                    .show()
                            }
                            .show()
                        return true
                    }
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun restartActivity() {
        val intent = activity?.intent
        activity?.finish()
        startActivity(intent!!)
    }

    private fun restartApp() {
        val ctx: Context = requireActivity().applicationContext
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(ctx.packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
        ctx.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}