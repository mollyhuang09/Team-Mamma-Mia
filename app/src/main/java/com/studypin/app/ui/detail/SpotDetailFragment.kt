package com.studypin.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpot
import com.studypin.app.ui.review.StarRatingViews
import java.util.Locale

class SpotDetailFragment : Fragment() {
    private var spotListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spot_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString("spotId") ?: ""
        spotListener = StudySpotRepository.observeSpot(
            spotId = spotId,
            onSuccess = { spot ->
                if (!isAdded) return@observeSpot
                if (spot == null) showMissingSpot(view) else bindSpot(view, spot)
            },
            onError = { if (isAdded) showMissingSpot(view) }
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
        super.onDestroyView()
    }

    private fun setupClickableStars(view: View, spotId: String) {
        val actionLayout = view.findViewById<View>(R.id.layoutUserRatingAction)
        if (ReviewRepository.hasUserReviewedSpot("You", spotId)) {
            actionLayout.visibility = View.GONE
            return
        }
        actionLayout.visibility = View.VISIBLE
        
        val layout = view.findViewById<LinearLayout>(R.id.layoutClickableStars)
        layout.removeAllViews() // Clear existing stars to avoid duplicates
        val starRow = StarRatingViews.buildStarRow(requireContext(), 0, true, 36f) { rating ->
            val bundle = Bundle().apply {
                putString("spotId", spotId)
                putInt("initialRating", rating)
            }
            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
        }
        layout.addView(starRow)
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
        listOf(R.id.tvAvailability, R.id.tvOccupancyDetail, R.id.tvRatingDetail,
            R.id.tvLocation, R.id.tvHours, R.id.tvAmenitiesDetail, R.id.btnEdit, R.id.btnReport)
            .forEach { view.findViewById<View>(it).visibility = View.GONE }
    }
}
