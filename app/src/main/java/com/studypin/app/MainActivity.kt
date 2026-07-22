package com.studypin.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.studypin.app.location.LocationReminderManager

class MainActivity : AppCompatActivity() {
    private val initialPermissionPreferences by lazy {
        getSharedPreferences("initial_permissions", MODE_PRIVATE)
    }

    private val foregroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestBackgroundLocationPermission()
        } else {
            finishInitialPermissionFlow("Precise location is needed for GPS reminders")
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestNotificationPermission()
        } else {
            finishInitialPermissionFlow("Allow background location to receive reminders after leaving")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            finishInitialPermissionFlow("Notifications are needed for availability reminders")
        } else {
            finishInitialPermissionFlow(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        openSpotFromReminder(intent)
        requestInitialPermissionsIfNeeded()

        bottomNav.setOnItemSelectedListener { item ->
            // Pop everything off the back stack down to (and including)
            // the tapped destination, so re-tapping "List" always shows
            // List itself, not a stale Detail/Group screen on top of it.
            navController.popBackStack(item.itemId, false)
            navController.navigate(item.itemId)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSpotFromReminder(intent)
    }

    private fun openSpotFromReminder(intent: Intent?) {
        val spotId = intent?.getStringExtra("spot_id") ?: return
        val bundle = Bundle().apply { putString("spotId", spotId) }
        supportFragmentManager.executePendingTransactions()
        val navController = (supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
            ?.navController ?: return
        navController.navigate(R.id.spotDetailFragment, bundle)
        intent.removeExtra("spot_id")
    }

    private fun requestInitialPermissionsIfNeeded() {
        if (initialPermissionPreferences.getBoolean("prompted", false)) return

        when {
            !LocationReminderManager.hasFineLocationPermission(this) -> {
                foregroundLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            else -> requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !LocationReminderManager.hasBackgroundLocationPermission(this)
        ) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finishInitialPermissionFlow(null)
        }
    }

    private fun finishInitialPermissionFlow(message: String?) {
        initialPermissionPreferences.edit().putBoolean("prompted", true).apply()
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }
}
