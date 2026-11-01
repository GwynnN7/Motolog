package com.gwynn7.motolog

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ui.setupWithNavController
import com.gwynn7.motolog.databinding.ActivityMainBinding

val Context.settings: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UnitHelper.loadData(applicationContext, lifecycleScope)
        val navController = getNavController()

        binding.bottomNavView.setupWithNavController(navController)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onSupportNavigateUp(): Boolean {
        return getNavController().navigateUp() || super.onSupportNavigateUp()
    }

    private fun getNavController(): NavController {
        val navHost = supportFragmentManager.findFragmentById(R.id.homeFragmentView) as NavHostFragment
        return navHost.navController
    }
}
