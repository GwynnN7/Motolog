package com.gwynn7.motolog

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.gwynn7.motolog.ViewModel.MotorcycleViewModel
import com.gwynn7.motolog.databinding.ActivityBikeBinding

class BikeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBikeBinding
    private val motorcycleViewModel: MotorcycleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bikeId = intent.getIntExtra(EXTRA_BIKE_ID, NO_BIKE_ID)
        if (bikeId == NO_BIKE_ID) {
            finish()
            return
        }
        motorcycleViewModel.selectedBikeId = bikeId

        binding = ActivityBikeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = getNavController()
        setupActionBarWithNavController(navController)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navController.navigateUp()) {
                    finish()
                }
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        return getNavController().navigateUp() || super.onSupportNavigateUp()
    }

    private fun getNavController(): NavController {
        val navHost = supportFragmentManager.findFragmentById(R.id.bike_home_nav) as NavHostFragment
        return navHost.navController
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    companion object {
        const val EXTRA_BIKE_ID = "bike_id"
        private const val NO_BIKE_ID = -1
    }
}
