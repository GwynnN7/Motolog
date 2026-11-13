package com.gwynn7.motolog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.gwynn7.motolog.databinding.ActivityMainBinding
import com.gwynn7.motolog.rides.tracking.RideRecorderService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val navController: NavController
        get() = (supportFragmentManager.findFragmentById(R.id.homeFragmentView) as NavHostFragment).navController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install before restoration as well as new navigation. Bike screens use the same
        // NavController operation for Back and toolbar Up. In particular, do not let
        // FragmentManager partially pop these screens during a predictive-back gesture.
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, fragment: Fragment, view: View, state: Bundle?) {
                val controller = (fragment.parentFragment as? NavHostFragment)?.navController ?: return
                if (controller.currentDestination?.id !in setOf(R.id.gear_show, R.id.gear_add, R.id.motorcycle_add) &&
                    controller.currentDestination?.hierarchy?.none { it.id == R.id.bike_nav } != false) return
                onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner) {
                    if (fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.navigateUp()
                }
            }
        }, true)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        UnitHelper.loadData(applicationContext, lifecycleScope)
        val topLevel = setOf(R.id.motorcycle_list, R.id.gear_list, R.id.rides_list, R.id.settings)
        setupActionBarWithNavController(navController, AppBarConfiguration(topLevel))
        binding.bottomNavView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.start_ride_placeholder) { openRecorder(resetNavigation = true); false }
            else { openTabRoot(item.itemId); true }
        }
        binding.bottomNavView.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.start_ride_placeholder) openRecorder(resetNavigation = true)
            else openTabRoot(item.itemId)
        }
        binding.startRide.setOnClickListener { openRecorder(resetNavigation = true) }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomBar.isVisible = destination.id != R.id.ride_recorder
            binding.toolbar.isVisible = destination.id != R.id.ride_recorder
            val tab = if (destination.id == R.id.ride_details) R.id.rides_nav else
                destination.hierarchy.firstOrNull { it.id in tabGraphs }?.id
            tab?.let { binding.bottomNavView.menu.findItem(it).isChecked = true }
        }
        if (intent.getBooleanExtra(RideRecorderService.OPEN_RECORDER, false)) {
            openRecorder()
            intent.removeExtra(RideRecorderService.OPEN_RECORDER)
        }
    }

    private val tabGraphs = setOf(R.id.motorcycle_nav, R.id.gear_nav, R.id.rides_nav, R.id.settings_nav)

    private fun rootNavigationOptions() = NavOptions.Builder()
        .setPopUpTo(R.id.home_nav, false)
        .setRestoreState(false)
        .setLaunchSingleTop(true)
        .build()

    private fun openTabRoot(graph: Int) {
        if (graph !in tabGraphs) return
        // Tab taps intentionally discard nested screens, including a reselected tab.
        navController.navigate(graph, null, rootNavigationOptions())
    }

    fun openRecorder(resetNavigation: Boolean = false) {
        if (navController.currentDestination?.id == R.id.ride_recorder) return
        val bikeId = runCatching { navController.getBackStackEntry(R.id.bike_nav).arguments?.getInt("bikeId") }.getOrNull() ?: -1
        navController.navigate(HomeNavDirections.openRideRecorder(bikeId),
            if (resetNavigation) rootNavigationOptions() else null)
    }

    fun returnToGarage() {
        if (navController.currentDestination?.id == R.id.motorcycle_list) return
        openTabRoot(R.id.motorcycle_nav)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(RideRecorderService.OPEN_RECORDER, false)) {
            openRecorder()
            intent.removeExtra(RideRecorderService.OPEN_RECORDER)
        }
    }

    override fun onSupportNavigateUp(): Boolean = navController.navigateUp() || super.onSupportNavigateUp()
    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.onAttach(base)) }
}
