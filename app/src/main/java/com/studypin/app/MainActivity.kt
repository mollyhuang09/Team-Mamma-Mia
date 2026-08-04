package com.studypin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.studypin.app.location.LocationReminderManager
import com.studypin.app.ui.showMessage

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

    private var pendingLeaveSpotId: String? = null
    private var pendingLeaveSpotName: String? = null

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
        navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.menu.findItem(destination.id)?.isChecked = true

            bottomNav.visibility = if (destination.id == R.id.reportFlagFragment ||
                destination.id == R.id.reportSuccessFragment
            ) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

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

    override fun onPostResume() {
        super.onPostResume()
        showPendingLeaveSpotPromptIfReady()
    }

    private fun openSpotFromReminder(intent: Intent?) {
        val spotId = intent?.getStringExtra("spot_id") ?: return
        val bundle = Bundle().apply { putString("spotId", spotId) }
        supportFragmentManager.executePendingTransactions()
        navController.navigate(R.id.spotDetailFragment, bundle)
        if (intent.getBooleanExtra(LocationReminderManager.EXTRA_SHOW_PROMPT, false)) {
            pendingLeaveSpotId = spotId
            pendingLeaveSpotName = intent.getStringExtra(LocationReminderManager.EXTRA_SPOT_NAME)
                ?: "this study spot"
            showPendingLeaveSpotPromptIfReady()
        }
        intent.removeExtra("spot_id")
        intent.removeExtra(LocationReminderManager.EXTRA_SHOW_PROMPT)
    }

    private fun showPendingLeaveSpotPromptIfReady() {
        if (pendingLeaveSpotId == null) {
            val pendingReminder = LocationReminderManager.pendingReminder(this)
            pendingLeaveSpotId = pendingReminder?.first
            pendingLeaveSpotName = pendingReminder?.second
        }

        val spotId = pendingLeaveSpotId ?: return
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return

        val spotName = pendingLeaveSpotName ?: "this study spot"
        pendingLeaveSpotId = null
        pendingLeaveSpotName = null
        startActivity(
            Intent(this, LeaveSpotPromptActivity::class.java)
                .putExtra(LocationReminderManager.EXTRA_SPOT_ID, spotId)
                .putExtra(LocationReminderManager.EXTRA_SPOT_NAME, spotName)
        )
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
        message?.let { showMessage(it, long = true) }
    }
}
