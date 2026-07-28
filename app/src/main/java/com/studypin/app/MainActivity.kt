package com.studypin.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.OccupancyRepository
import com.studypin.app.data.StudySpotRepository

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private var currentCheckInListener: ListenerRegistration? = null
    private var currentSpotListener: ListenerRegistration? = null
    private var currentSpotId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
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
