package com.studypin.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.studypin.app.data.CheckInManager
import android.widget.TextView
import android.view.View
import android.widget.Button
import androidx.navigation.NavController

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

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

        setupCheckInBanner()

        navController.addOnDestinationChangedListener { _, _, _ ->
            updateBannerVisibility()
        }
    }

    private fun setupCheckInBanner() {
        val banner = findViewById<View>(R.id.layoutCheckInBanner)
        val btnCheckOut = banner.findViewById<Button>(R.id.btnCheckOut)

        btnCheckOut.setOnClickListener {
            val spotId = CheckInManager.currentSpotId
            CheckInManager.checkOut()
            updateBannerVisibility()
            
            // Navigate to add review
            if (spotId != null) {
                val bundle = Bundle().apply {
                    putString("spotId", spotId)
                }
                navController.navigate(R.id.addReviewFragment, bundle)
            }
        }
    }

    fun updateBannerVisibility() {
        val banner = findViewById<View>(R.id.layoutCheckInBanner)
        val spot = CheckInManager.getCurrentSpot()

        if (spot != null) {
            banner.visibility = View.VISIBLE
            banner.findViewById<TextView>(R.id.tvCheckInStatus).text = "Checked into ${spot.name}"
        } else {
            banner.visibility = View.GONE
        }
    }
}