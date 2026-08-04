package com.studypin.app.ui.review

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.studypin.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.ReviewRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.StudySpotReview
import com.studypin.app.ui.applyStatusBarInset
import com.studypin.app.utils.ImageCaptureHelper
import com.studypin.app.ui.showMessage
import java.util.UUID

class AddReviewFragment : Fragment() {

    private var spotId: String = ""
    private var initialRating: Int = 0
    private var verifyMode: Boolean = false
    private var selectedOverallRating = 0
    private var selectedImageBitmap: Bitmap? = null
    private val amenityRatings = mutableMapOf<String, Int>()
    private var spotListener: ListenerRegistration? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPickedImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_add_review, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.applyStatusBarInset()

        spotId = arguments?.getString("spotId") ?: ""
        initialRating = arguments?.getInt("initialRating") ?: 0
        verifyMode = arguments?.getBoolean("verifyMode") ?: false

        view.findViewById<TextView>(R.id.tvSpotName).text = "Loading spot..."

        if (verifyMode) {
            view.findViewById<TextView>(R.id.tvReviewTitle).text = "Verify this spot"
            view.findViewById<MaterialButton>(R.id.btnSubmitReview).text = "Submit Verification"
            view.findViewById<TextView>(R.id.tvPhotoRequiredHint).visibility = View.VISIBLE
        }

        view.findViewById<View>(R.id.btnAddMedia).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        setupOverallStars(view)
        spotListener = StudySpotRepository.observeSpot(
            spotId = spotId,
            onSuccess = { spot ->
                if (!isAdded) return@observeSpot
                if (spot == null) {
                    view.findViewById<TextView>(R.id.tvSpotName).text = "Unknown Spot"
                } else {
                    view.findViewById<TextView>(R.id.tvSpotName).text = spot.name
                    val allAmenities = (listOf("noise", "seating") + spot.amenities)
                        .distinct()
                        .filterNot { it.equals("printing", ignoreCase = true) }
                    setupAmenityRatings(view, allAmenities)
                }
            },
            onError = {
                if (isAdded) view.findViewById<TextView>(R.id.tvSpotName).text = "Spot unavailable"
            }
        )

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnSubmitReview).setOnClickListener {
            submitReview(view)
        }

        if (FirebaseAuth.getInstance().currentUser == null) {
            view.findViewById<View>(R.id.btnSubmitReview).isEnabled = false
            showMessage("Sign in to submit a review")
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

    private fun processPickedImage(uri: Uri) {
        ImageCaptureHelper.processPickedImage(
            context = requireContext(),
            uri = uri,
            onProcessed = { bitmap, facesBlurred ->
                if (!isAdded) return@processPickedImage
                selectedImageBitmap = bitmap
                view?.findViewById<ImageView>(R.id.ivReviewPhoto)?.apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE
                }
                if (facesBlurred) {
                    showMessage("Faces blurred for privacy")
                }
            },
            onFailure = {
                if (isAdded) showMessage("Could not load photo")
            }
        )
    }

    private fun submitReview(view: View) {
        if (selectedOverallRating == 0) {
            showMessage("Please select a rating")
            return
        }
        if (verifyMode && selectedImageBitmap == null) {
            showMessage("A photo is required to verify this spot")
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (verifyMode && userId == null) {
            showMessage("Sign in to verify this spot")
            return
        }

        val reviewText = view.findViewById<TextInputEditText>(R.id.etReviewText).text.toString()
        val visitTime = getSelectedChipText(view.findViewById(R.id.chipGroupVisitTime))
        val crowdLevel = getSelectedChipText(view.findViewById(R.id.chipGroupCrowd))
        val descriptors = getSelectedChipTexts(view.findViewById(R.id.chipGroupDescriptors))
        val reviewId = UUID.randomUUID().toString()

        view.findViewById<View>(R.id.btnSubmitReview).isEnabled = false

        if (verifyMode) {
            StudySpotRepository.vouchSpot(spotId, userId!!)
                .addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    showMessage(getString(R.string.spot_vouched))
                    uploadPhotoAndSaveReview(reviewId, reviewText, visitTime, crowdLevel, descriptors)
                }
                .addOnFailureListener { error ->
                    if (isAdded) {
                        view.findViewById<View>(R.id.btnSubmitReview).isEnabled = true
                        showMessage(error.message ?: "Could not verify this spot", long = true)
                    }
                }
        } else {
            saveReview(reviewId, reviewText, visitTime, crowdLevel, descriptors, photoUrl = null)
        }
    }

    private fun uploadPhotoAndSaveReview(
        reviewId: String,
        reviewText: String,
        visitTime: String,
        crowdLevel: String,
        descriptors: List<String>
    ) {
        val bitmap = selectedImageBitmap
        if (bitmap == null) {
            saveReview(reviewId, reviewText, visitTime, crowdLevel, descriptors, photoUrl = null)
            return
        }
        StudySpotRepository.uploadSpotImage(
            spotId = spotId,
            bitmap = bitmap,
            onSuccess = { photoUrl ->
                if (isAdded) saveReview(reviewId, reviewText, visitTime, crowdLevel, descriptors, photoUrl)
            },
            onError = { error ->
                if (isAdded) {
                    view?.findViewById<View>(R.id.btnSubmitReview)?.isEnabled = true
                    showMessage("Verified, but photo upload failed: ${error.message}", long = true)
                }
            }
        )
    }

    private fun saveReview(
        reviewId: String,
        reviewText: String,
        visitTime: String,
        crowdLevel: String,
        descriptors: List<String>,
        photoUrl: String?
    ) {
        val review = StudySpotReview(
            id = reviewId,
            spotId = spotId,
            reviewerId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous",
            reviewerName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Anonymous",
            overallRating = selectedOverallRating,
            amenityRatings = amenityRatings.toMap(),
            descriptors = descriptors,
            reviewText = reviewText,
            visitTimeOfDay = visitTime,
            crowdLevel = crowdLevel,
            mediaCount = if (photoUrl != null) 1 else 0,
            photoUrl = photoUrl,
            submittedAtLabel = "Just now"
        )

        ReviewRepository.addReview(review) { success, error ->
            if (!isAdded) return@addReview
            view?.findViewById<View>(R.id.btnSubmitReview)?.isEnabled = true
            if (success) {
                showMessage("Review posted and saved to cloud!")
                findNavController().navigateUp()
            } else {
                showMessage("Could not save review: $error", long = true)
            }
        }
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

    override fun onDestroyView() {
        spotListener?.remove()
        spotListener = null
        super.onDestroyView()
    }
}
