package com.studypin.app.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpotReview
import com.studypin.app.ui.applyStatusBarInset
import com.studypin.app.ui.showPhotoUploadPlaceholder
import java.util.UUID

class AddReviewFragment : Fragment() {

    private var spotId: String = ""
    private var initialRating: Int = 0
    private var selectedOverallRating = 0
    private val amenityRatings = mutableMapOf<String, Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_add_review, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.applyStatusBarInset()

        spotId = arguments?.getString("spotId") ?: ""
        initialRating = arguments?.getInt("initialRating") ?: 0

        val spot = MockData.studySpots.firstOrNull { it.id == spotId }
        view.findViewById<TextView>(R.id.tvSpotName).text = spot?.name ?: "Unknown Spot"

        setupOverallStars(view)
        
        val baseAmenities = listOf("noise", "seating")
        val allAmenities = (baseAmenities + (spot?.amenities ?: emptyList()))
            .distinct()
            .filterNot { it.equals("printing", ignoreCase = true) }
        setupAmenityRatings(view, allAmenities)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnSubmitReview).setOnClickListener {
            submitReview(view)
        }

        view.findViewById<View>(R.id.btnAddMedia).setOnClickListener {
            requireContext().showPhotoUploadPlaceholder()
        }

        // Set initial rating if passed from previous screen
        if (initialRating > 0) {
            updateOverallRating(view, initialRating)
        } else {
            updateSubmitButtonState(view)
        }
    }

    private fun setupOverallStars(view: View) {
        val layout = view.findViewById<LinearLayout>(R.id.layoutOverallStars)
        val starRow = StarRatingViews.buildStarRow(requireContext(), selectedOverallRating, true, 40f) { rating ->
            updateOverallRating(view, rating)
        }
        layout.removeAllViews()
        layout.addView(starRow)
    }

    private fun updateOverallRating(view: View, rating: Int) {
        selectedOverallRating = rating
        setupOverallStars(view)
        updateSubmitButtonState(view)
//        val label = when (rating) {
//            1 -> "Terrible"
//            2 -> "Bad"
//            3 -> "Okay"
//            4 -> "Good"
//            5 -> "Excellent"
//            else -> ""
//        }
//        view.findViewById<TextView>(R.id.tvSelectedRatingLabel).text = if (label.isEmpty()) "" else "$rating/5 - $label"
    }

    private fun updateSubmitButtonState(view: View) {
        view.findViewById<View>(R.id.btnSubmitReview).isEnabled = selectedOverallRating > 0
    }

    private fun setupAmenityRatings(view: View, amenities: List<String>) {
        val layout = view.findViewById<LinearLayout>(R.id.layoutAmenityRatings)
        layout.removeAllViews()

        amenities.forEach { amenity ->
            val amenityView = LayoutInflater.from(requireContext()).inflate(R.layout.item_amenity_rating_input, layout, false)
            val label = when (amenity) {
                "noise" -> "Noise Level"
                "seating" -> "Seating Comfort"
                "wifi" -> "WiFi Strength"
                "outlets" -> "Outlet Availability"
                else -> amenity.replaceFirstChar { it.uppercase() }
            }
            amenityView.findViewById<TextView>(R.id.tvAmenityName).text = label
            
            val starsLayout = amenityView.findViewById<LinearLayout>(R.id.layoutAmenityStars)
            val currentRating = amenityRatings[amenity] ?: 0
            val starRow = StarRatingViews.buildStarRow(requireContext(), currentRating, true, 24f) { rating ->
                amenityRatings[amenity] = rating
                setupAmenityRatings(view, amenities) // Refresh to update stars
            }
            starsLayout.removeAllViews()
            starsLayout.addView(starRow)
            
            layout.addView(amenityView)
        }
    }

    private fun submitReview(view: View) {
        if (selectedOverallRating == 0) {
            Toast.makeText(requireContext(), "Please select a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val reviewText = view.findViewById<TextInputEditText>(R.id.etReviewText).text.toString()
        val visitTime = getSelectedChipText(view.findViewById(R.id.chipGroupVisitTime))
        val crowdLevel = getSelectedChipText(view.findViewById(R.id.chipGroupCrowd))
        val descriptors = getSelectedChipTexts(view.findViewById(R.id.chipGroupDescriptors))

        val review = StudySpotReview(
            id = UUID.randomUUID().toString(),
            spotId = spotId,
            reviewerName = "You", // In a real app, this would be the logged-in user
            overallRating = selectedOverallRating,
            amenityRatings = amenityRatings.toMap(),
            descriptors = descriptors,
            reviewText = reviewText,
            visitTimeOfDay = visitTime,
            crowdLevel = crowdLevel,
            submittedAtLabel = "Just now"
        )

        ReviewRepository.addReview(review) { success, error ->
            if (success) {
                Toast.makeText(requireContext(), "Review posted and saved to cloud!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Saved locally, but cloud sync failed: $error", Toast.LENGTH_LONG).show()
            }
        }
        findNavController().navigateUp()
    }

    private fun getSelectedChipText(chipGroup: ChipGroup): String {
        val checkedId = chipGroup.checkedChipId
        return if (checkedId != View.NO_ID) {
            chipGroup.findViewById<Chip>(checkedId)?.text?.toString() ?: ""
        } else {
            ""
        }
    }

    private fun getSelectedChipTexts(chipGroup: ChipGroup): List<String> {
        return chipGroup.checkedChipIds.mapNotNull { checkedId ->
            chipGroup.findViewById<Chip>(checkedId)?.text?.toString()
        }
    }
}
