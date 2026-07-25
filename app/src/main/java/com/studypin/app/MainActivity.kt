package com.studypin.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

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

        bottomNav.setOnItemSelectedListener { item ->
            // Pop everything off the back stack down to (and including)
            // the tapped destination, so re-tapping "List" always shows
            // List itself, not a stale Detail/Group screen on top of it.
            navController.popBackStack(item.itemId, false)
            navController.navigate(item.itemId)
            true
        }
    }
}
