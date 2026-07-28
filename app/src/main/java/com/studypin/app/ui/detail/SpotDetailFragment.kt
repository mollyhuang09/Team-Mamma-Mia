package com.studypin.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.R
import com.studypin.app.data.OccupancyRepository
import com.studypin.app.data.ReviewRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.location.LocationReminderManager
import com.studypin.app.model.StudySpot
import com.studypin.app.ui.review.StarRatingViews
import com.studypin.app.utils.LocationUtils
import java.util.Locale

class SpotDetailFragment : Fragment() {
    private var spotListener: ListenerRegistration? = null
    private var checkInListener: ListenerRegistration? = null
    private var currentSpot: StudySpot? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spot_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString("spotId") ?: ""
        setupCheckInButton(view, spotId)
        setupTrackVisitButton(view)
        spotListener = StudySpotRepository.observeSpot(
            spotId = spotId,
            onSuccess = { spot ->
                if (!isAdded) return@observeSpot
                currentSpot = spot
                if (spot == null) {
                    showMissingSpot(view)
                } else {
                    bindSpot(view, spot)
                    updateTrackVisitButton(view.findViewById(R.id.btnTrackVisit), spot)
                }
            },
            onError = {
                if (isAdded) showMissingSpot(view)
            }
        )

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<Button>(R.id.btnEdit).setOnClickListener {
            findNavController().navigate(R.id.action_spotDetail_to_editSpot)
        }
        view.findViewById<Button>(R.id.btnReport).setOnClickListener {
            val spotName = arguments?.getString("spotName")

            val bundle = Bundle().apply {
                putString("spotId", spotId)
                spotName?.let { putString("spotName", it) }
            }

            findNavController().navigate(R.id.action_spotDetail_to_reportFlag, bundle)
        }

        setupClickableStars(view, spotId)

        view.findViewById<View>(R.id.tvRatingsHeader).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_reviewList, bundle)
        }
        view.findViewById<View>(R.id.tvRatingDetail).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_reviewList, bundle)
        }
    }

    override fun onDestroyView() {
        spotListener?.remove()
        spotListener = null
        checkInListener?.remove()
        checkInListener = null
        currentSpot = null
        super.onDestroyView()
    }

    private fun setupTrackVisitButton(view: View) {
        view.findViewById<Button>(R.id.btnTrackVisit).setOnClickListener {
            val spot = currentSpot
            if (spot == null) {
                showTrackingMessage("Study spot details are still loading")
                return@setOnClickListener
            }

            val button = it as Button
            if (LocationReminderManager.isTracking(requireContext(), spot.id)) {
                LocationReminderManager.stopTracking(requireContext(), spot.id)
                button.text = "I’m at this study spot"
                showTrackingMessage("Location reminders stopped")
            } else {
                verifyUserIsAtSpotAndStartTracking(spot, button)
            }
        }
    }

    private fun updateTrackVisitButton(button: Button, spot: StudySpot) {
        button.text = if (LocationReminderManager.isTracking(requireContext(), spot.id)) {
            "Stop tracking this visit"
        } else {
            "I’m at this study spot"
        }
    }

    private fun verifyUserIsAtSpotAndStartTracking(spot: StudySpot, button: Button) {
        if (!LocationReminderManager.hasFineLocationPermission(requireContext()) ||
            !LocationReminderManager.hasBackgroundLocationPermission(requireContext())
        ) {
            showTrackingMessage("Enable location permissions in Settings before tracking a visit")
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(requireContext())
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    showTrackingMessage("Couldn’t determine your current location")
                    return@addOnSuccessListener
                }

                val distanceMeters = LocationUtils.distanceInMeters(
                    location.latitude,
                    location.longitude,
                    spot.latitude,
                    spot.longitude
                )
                if (distanceMeters > LocationReminderManager.GEOFENCE_RADIUS_METERS) {
                    showTrackingMessage(
                        "Hmm, it seems like you’re not at this study spot yet. " +
                            "Move within ${LocationReminderManager.GEOFENCE_RADIUS_METERS.toInt()} m to start tracking."
                    )
                    return@addOnSuccessListener
                }

                startTrackingForSpot(spot, button)
            }
            .addOnFailureListener {
                showTrackingMessage("Couldn’t determine your current location")
            }
    }

    private fun startTrackingForSpot(spot: StudySpot, button: Button) {
        LocationReminderManager.startTracking(
            requireContext(),
            spot,
            onSuccess = {
                button.text = "Stop tracking this visit"
                showTrackingMessage("Visit tracked. You’ll be reminded after you leave")
            },
            onFailure = { exception ->
                showTrackingMessage(locationReminderErrorMessage(exception))
            }
        )
    }

    private fun locationReminderErrorMessage(exception: Exception?): String {
        if (exception is SecurityException) {
            return "Allow background location for StudyPin in Settings, then try again"
        }

        val statusCode = (exception as? ApiException)?.statusCode
        return when (statusCode) {
            GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                "Location services are unavailable. Turn on Location and try again"
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                "Too many location reminders are active. Stop another visit first"
            GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                "Too many location reminders are active. Restart StudyPin and try again"
            CommonStatusCodes.API_NOT_CONNECTED,
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
                "This emulator needs Google Play services for location reminders"
            else -> {
                val code = statusCode?.let { " Error code: $it." } ?: ""
                "Couldn’t start the location reminder.$code Use a Google Play emulator image and allow background location."
            }
        }
    }

    private fun showTrackingMessage(message: String) {
        val root = view ?: return
        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG)
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.apply {
                maxLines = 4
                textSize = 16f
            }
        snackbar.show()
    }

    private fun setupClickableStars(view: View, spotId: String) {
        val actionLayout = view.findViewById<View>(R.id.layoutUserRatingAction)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            actionLayout.visibility = View.GONE
            return
        }
        val currentUserId = currentUser.uid
        if (ReviewRepository.hasUserReviewedSpot(currentUserId, spotId)) {
            actionLayout.visibility = View.GONE
            return
        }
        actionLayout.visibility = View.VISIBLE

        val layout = view.findViewById<LinearLayout>(R.id.layoutClickableStars)
        layout.removeAllViews()
        val starRow = StarRatingViews.buildStarRow(requireContext(), 0, true, 36f) { rating ->
            val bundle = Bundle().apply {
                putString("spotId", spotId)
                putInt("initialRating", rating)
            }
            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
        }
        layout.addView(starRow)
    }

    private fun setupCheckInButton(view: View, spotId: String) {
        val button = view.findViewById<Button>(R.id.btnCheckInToggle)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null || spotId.isBlank()) {
            button.text = "Sign in to check in"
            button.isEnabled = false
            return
        }

        button.isEnabled = false
        checkInListener = OccupancyRepository.observeCheckIn(
            spotId = spotId,
            userId = userId,
            onSuccess = { checkedIn ->
                if (!isAdded) return@observeCheckIn
                button.isEnabled = true
                button.text = if (checkedIn) "Check Out" else "Check In"
            },
            onError = {
                if (isAdded) {
                    button.isEnabled = false
                    button.text = "Check-in unavailable"
                }
            }
        )

        button.setOnClickListener {
            button.isEnabled = false
            val checkedIn = button.text == "Check Out"
            val operation = if (checkedIn) {
                OccupancyRepository.checkOut(spotId, userId)
            } else {
                OccupancyRepository.checkIn(spotId, userId)
            }
            operation
                .addOnFailureListener { error ->
                    if (isAdded) {
                        button.isEnabled = true
                        Toast.makeText(requireContext(), "Could not update check-in: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun bindSpot(view: View, spot: StudySpot) {
        val stats = ReviewRepository.displayStatsForSpot(spot)
        view.findViewById<TextView>(R.id.tvSpotName).text = spot.name
        view.findViewById<TextView>(R.id.tvHiddenGem).visibility =
            if (spot.isHiddenGem) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvAvailability).text = spot.occupancyLabel()
        view.findViewById<TextView>(R.id.tvOccupancyDetail).text =
            "${spot.currentCheckIns} / ${spot.capacity.approxSeats} seats occupied (${spot.capacity.label})"

        view.findViewById<TextView>(R.id.tvRatingDetail).text = String.format(
            Locale.CANADA, "★ %.1f / 5 (%d rating%s)", stats.averageOverall, stats.reviewCount,
            if (stats.reviewCount == 1) "" else "s"
        )

        bindCategoryRatings(view, spot, stats)

        view.findViewById<TextView>(R.id.tvDescription).text = spot.description
        view.findViewById<TextView>(R.id.tvLocation).text = spot.address
        view.findViewById<TextView>(R.id.tvHours).text = spot.hours
        view.findViewById<TextView>(R.id.tvAmenitiesDetail).text = if (spot.amenities.isEmpty()) {
            "No amenities listed"
        } else {
            spot.amenities.joinToString(" · ") { it.replaceFirstChar(Char::titlecase) }
        }
    }

    private fun bindCategoryRatings(view: View, spot: StudySpot, stats: com.studypin.app.model.ReviewDisplayStats) {
        val layout = view.findViewById<View>(R.id.layoutCategoryRatings)

        if (stats.reviewCount == 0) {
            layout.visibility = View.GONE
            return
        }

        layout.visibility = View.VISIBLE

        val noiseVal = stats.amenityAverages["noise"] ?: Double.NaN
        val wifiVal = stats.amenityAverages["wifi"] ?: Double.NaN
        val seatingVal = stats.amenityAverages["seating"] ?: Double.NaN
        val outletsVal = stats.amenityAverages["outlets"] ?: Double.NaN

        val tvNoise = view.findViewById<TextView>(R.id.tvNoiseRating)
        val tvWifi = view.findViewById<TextView>(R.id.tvWifiRating)
        val tvSeating = view.findViewById<TextView>(R.id.tvSeatingRating)
        val tvOutlets = view.findViewById<TextView>(R.id.tvOutletsRating)

        tvNoise.text = "Noise Level: ${formatRating(noiseVal)}"
        tvNoise.visibility = if (noiseVal.isNaN()) View.GONE else View.VISIBLE

        tvWifi.text = "WiFi Strength: ${formatRating(wifiVal)}"
        tvWifi.visibility = if (wifiVal.isNaN()) View.GONE else View.VISIBLE

        tvSeating.text = "Seating Comfort: ${formatRating(seatingVal)}"
        tvSeating.visibility = if (seatingVal.isNaN()) View.GONE else View.VISIBLE

        tvOutlets.text = "Outlet Availability: ${formatRating(outletsVal)}"
        tvOutlets.visibility = if (outletsVal.isNaN()) View.GONE else View.VISIBLE
    }

    private fun formatRating(value: Double): String {
        return if (value.isNaN()) "-" else String.format(Locale.CANADA, "%.1f / 5", value)
    }

    private fun showMissingSpot(view: View) {
        view.findViewById<TextView>(R.id.tvSpotName).text = "Study spot unavailable"
        view.findViewById<TextView>(R.id.tvDescription).text =
            "This study spot could not be found. Return to the list and choose another spot."
        listOf(
            R.id.tvAvailability,
            R.id.tvOccupancyDetail,
            R.id.tvRatingDetail,
            R.id.tvLocation,
            R.id.tvHours,
            R.id.tvAmenitiesDetail,
            R.id.btnEdit,
            R.id.btnReport,
            R.id.btnCheckInToggle,
            R.id.btnTrackVisit
        ).forEach { view.findViewById<View>(it).visibility = View.GONE }
    }
}
