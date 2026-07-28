package com.studypin.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.OccupancyRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.location.LocationReminderManager

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private var currentCheckInListener: ListenerRegistration? = null
    private var currentSpotListener: ListenerRegistration? = null
    private var currentSpotId: String? = null

    private var pendingLeaveSpotId: String? = null
    private var pendingLeaveSpotName: String? = null
    private var leavePromptVisible = false

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

        findViewById<Button>(R.id.btnCheckOut).setOnClickListener {
            checkOutCurrentSpot()
        }
    }

    override fun onStart() {
        super.onStart()
        observeCurrentCheckIn()
    }

    override fun onStop() {
        currentCheckInListener?.remove()
        currentCheckInListener = null
        currentSpotListener?.remove()
        currentSpotListener = null
        super.onStop()
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
        if (leavePromptVisible) return
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
        leavePromptVisible = true
        showLeaveSpotPrompt(spotId, spotName)
    }

    private fun showLeaveSpotPrompt(spotId: String, spotName: String) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_leave_spot, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLeft)
            .setOnClickListener {
                LocationReminderManager.clearPendingReminder(this, spotId)
                LocationReminderManager.stopTracking(this, spotId)
                Toast.makeText(this, "Thanks for updating the study spot", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStillHere)
            .setOnClickListener {
                LocationReminderManager.clearPendingReminder(this, spotId)
                LocationReminderManager.cancelReminder(this, spotId)
                LocationReminderManager.setEntered(this, spotId, true)
                Toast.makeText(this, "Okay, we’ll keep tracking your visit", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.setOnDismissListener {
            leavePromptVisible = false
        }
        dialog.show()
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

    private fun observeCurrentCheckIn() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            updateCheckInBanner(null, null)
            return
        }

        currentCheckInListener = OccupancyRepository.observeCurrentCheckIn(
            userId = userId,
            onSuccess = { spotId ->
                currentSpotId = spotId
                currentSpotListener?.remove()
                currentSpotListener = if (spotId == null) {
                    updateCheckInBanner(null, null)
                    null
                } else {
                    StudySpotRepository.observeSpot(
                        spotId = spotId,
                        onSuccess = { spot -> updateCheckInBanner(spotId, spot?.name) },
                        onError = { updateCheckInBanner(spotId, null) }
                    )
                }
            },
            onError = { updateCheckInBanner(null, null) }
        )
    }

    private fun checkOutCurrentSpot() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val spotId = currentSpotId
        if (userId == null || spotId == null) return

        findViewById<Button>(R.id.btnCheckOut).isEnabled = false
        OccupancyRepository.checkOut(spotId, userId)
            .addOnSuccessListener {
                Toast.makeText(this, "Checked out", Toast.LENGTH_SHORT).show()
                val bundle = Bundle().apply { putString("spotId", spotId) }
                navController.navigate(R.id.addReviewFragment, bundle)
            }
            .addOnFailureListener { error ->
                findViewById<Button>(R.id.btnCheckOut).isEnabled = true
                Toast.makeText(this, "Could not check out: ${error.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateCheckInBanner(spotId: String?, spotName: String?) {
        val banner = findViewById<View>(R.id.layoutCheckInBanner)
        val checkOutButton = findViewById<Button>(R.id.btnCheckOut)
        currentSpotId = spotId
        if (spotId == null) {
            banner.visibility = View.GONE
            checkOutButton.isEnabled = true
        } else {
            banner.visibility = View.VISIBLE
            banner.findViewById<TextView>(R.id.tvCheckInStatus).text =
                "Checked into ${spotName ?: "study spot"}"
            checkOutButton.isEnabled = true
        }
    }
}
