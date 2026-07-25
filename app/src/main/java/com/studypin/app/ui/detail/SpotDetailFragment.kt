package com.studypin.app.ui.detail

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpot
import com.studypin.app.model.StudySpotReview
import com.studypin.app.ui.review.ReviewHelpfulBinder
import com.studypin.app.ui.review.StarRatingViews
import com.studypin.app.ui.setOnApplyStatusBarInsetsListener
import com.studypin.app.ui.toTagLabel
import java.util.Locale

class SpotDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spot_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString("spotId") ?: ""
        val spot = MockData.studySpots.firstOrNull { it.id == spotId }

        if (spot == null) {
            showMissingSpot(view)
            return
        }

        bindSpot(view, spot)
        applyStatusBarInsets(view)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnReport).setOnClickListener {
            navigateToReport(spotId, spot.name)
        }

        view.findViewById<View>(R.id.btnShare).setOnClickListener {
            Toast.makeText(requireContext(), "Sharing will be connected later", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btnEdit).setOnClickListener {
            findNavController().navigate(R.id.action_spotDetail_to_editSpot)
        }
        view.findViewById<View>(R.id.btnDirection).setOnClickListener {
            Toast.makeText(requireContext(), "Directions will be connected later", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.btnSave).setOnClickListener { button ->
            val saveButton = button as MaterialButton
            saveButton.text = if (saveButton.text == "Save") "Saved" else "Save"
        }
        view.findViewById<View>(R.id.btnAddPhoto).setOnClickListener {
            Toast.makeText(requireContext(), "Photo upload will be connected later", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnAddReview).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
        }

        view.findViewById<View>(R.id.tvRatingDetail).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_reviewList, bundle)
        }

        if (ReviewRepository.hasUserReviewedSpot("You", spotId)) {
            view.findViewById<View>(R.id.btnAddReview).visibility = View.GONE
        }
    }

    private fun navigateToReport(spotId: String, spotName: String) {
        val bundle = Bundle().apply {
            putString("spotId", spotId)
            putString("spotName", spotName)
        }
        findNavController().navigate(R.id.action_spotDetail_to_reportFlag, bundle)
    }

    private fun applyStatusBarInsets(view: View) {
        val heroContainer = view.findViewById<View>(R.id.heroContainer)
        val backButton = view.findViewById<View>(R.id.btnBack)
        val heroActions = view.findViewById<View>(R.id.heroActions)

        heroContainer.setOnApplyStatusBarInsetsListener { statusBarHeight ->
            val topMargin = statusBarHeight + dpToPx(4)

            backButton.updateLayoutParams<FrameLayout.LayoutParams> {
                this.topMargin = topMargin
            }
            heroActions.updateLayoutParams<FrameLayout.LayoutParams> {
                this.topMargin = topMargin
            }

            if (heroImageIsHidden(heroContainer)) {
                heroContainer.updateLayoutParams<LinearLayout.LayoutParams> {
                    height = statusBarHeight + dpToPx(16 + 48 + 8)
                }
            }

        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun heroImageIsHidden(heroContainer: View): Boolean =
        heroContainer.findViewById<ImageView>(R.id.ivSpotHero).visibility == View.GONE

    private fun bindSpot(view: View, spot: StudySpot) {
        view.findViewById<TextView>(R.id.tvSpotName).text = spot.name
        view.findViewById<TextView>(R.id.tvAvailability).text = spot.occupancyLabel()
        view.findViewById<TextView>(R.id.tvLocation).text = spot.address

        val hasGemConnection = spot.isHiddenGem
        view.findViewById<TextView>(R.id.tvHiddenGem).visibility =
            if (hasGemConnection) View.VISIBLE else View.GONE

        val ratingText = String.format(
            Locale.CANADA,
            "%.1f (%d Reviews)",
            spot.avgRating,
            spot.totalRatings
        )
        view.findViewById<TextView>(R.id.tvRatingDetail).text = ratingText
        view.findViewById<TextView>(R.id.tvReviewAverage).text =
            String.format(Locale.CANADA, "%.1f", spot.avgRating)
        view.findViewById<TextView>(R.id.tvReviewCount).text =
            "Based on ${spot.totalRatings} reviews"

        bindTags(view, spot)
        bindImages(view, spot)
        bindRatingBreakdown(view, spot)
        bindReviewCards(view, spot)
    }

    private fun bindTags(view: View, spot: StudySpot) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupTags)
        chipGroup.removeAllViews()

        spot.amenities.forEach { amenity ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_spot_tag, chipGroup, false) as Chip
            chip.isCheckable = false
            chip.isClickable = false
            chip.isFocusable = false
            chip.text = amenity.toTagLabel()
            chipGroup.addView(chip)
        }
    }

    private fun bindImages(view: View, spot: StudySpot) {
        val heroContainer = view.findViewById<View>(R.id.heroContainer)
        val detailPanel = view.findViewById<View>(R.id.detailPanel)
        val heroImage = view.findViewById<ImageView>(R.id.ivSpotHero)
        val photoHeader = view.findViewById<TextView>(R.id.tvPhotosHeader)
        val photoGallery = view.findViewById<HorizontalScrollView>(R.id.photoGallery)
        val photoGalleryContainer =
            view.findViewById<LinearLayout>(R.id.photoGalleryContainer)

        val imageUris = buildList {
            addAll(spot.imageUrls)
            spot.imageUrl?.let(::add)
        }
            .filter { it.isNotBlank() && it != "placeholder_uri" }
            .distinct()
            .map(Uri::parse)

        photoGalleryContainer.removeAllViews()

        if (imageUris.isEmpty()) {
            heroImage.visibility = View.GONE
            photoHeader.visibility = View.GONE
            photoGallery.visibility = View.GONE

            val compactHeight = (80 * resources.displayMetrics.density).toInt()
            heroContainer.layoutParams = heroContainer.layoutParams.apply {
                height = compactHeight
            }
            detailPanel.layoutParams = detailPanel.layoutParams.apply {
                if (this is LinearLayout.LayoutParams) topMargin = 0
            }
            return
        }

        val firstImageUri = imageUris.first()
        heroImage.visibility = View.VISIBLE
        heroImage.setImageURI(firstImageUri)

        photoHeader.visibility = View.VISIBLE
        photoGallery.visibility = View.VISIBLE

        imageUris.forEachIndexed { index, uri ->
            val imageWidth = if (index == 0) 262 else 148
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(imageWidth),
                    dpToPx(196)
                ).apply {
                    marginEnd = dpToPx(12)
                }
                contentDescription = "Spot photo ${index + 1}"
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
            }
            photoGalleryContainer.addView(imageView)
        }

        val fullHeight = (300 * resources.displayMetrics.density).toInt()
        heroContainer.layoutParams = heroContainer.layoutParams.apply {
            height = fullHeight
        }
        detailPanel.layoutParams = detailPanel.layoutParams.apply {
            if (this is LinearLayout.LayoutParams) {
                topMargin = (-18 * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun bindRatingBreakdown(view: View, spot: StudySpot) {
        val reviews = ReviewRepository.reviewsForSpot(spot.id)
        val counts = (1..5).associateWith { rating ->
            reviews.count { it.overallRating == rating }
        }
        val largestCount = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        (1..5).forEach { rating ->
            val count = counts[rating] ?: 0
            val progress = view.findViewById<ProgressBar>(ratingProgressId(rating))
            progress.progress = count * 100 / largestCount
            view.findViewById<TextView>(ratingCountId(rating)).text = count.toString()
        }
    }

    private fun bindReviewCards(view: View, spot: StudySpot) {
        val reviewContainer = view.findViewById<LinearLayout>(R.id.layoutReviewCards)
        reviewContainer.removeAllViews()

        ReviewRepository.reviewsForSpot(spot.id).take(3).forEach { review ->
            val reviewView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_review, reviewContainer, false)
            bindReviewView(reviewView, review)
            reviewContainer.addView(reviewView)
        }
    }

    private fun bindReviewView(view: View, review: StudySpotReview) {
        view.findViewById<TextView>(R.id.tvReviewerAvatar).text =
            review.reviewerName.firstOrNull()?.uppercase() ?: "?"
        view.findViewById<TextView>(R.id.tvReviewerName).text = review.reviewerName
        view.findViewById<TextView>(R.id.tvReviewSubmittedAt).text = review.submittedAtLabel
        view.findViewById<TextView>(R.id.tvReviewText).text = review.reviewText

        val meta = listOf(review.visitTimeOfDay, review.crowdLevel)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        view.findViewById<TextView>(R.id.tvReviewMeta).apply {
            text = meta
            visibility = if (meta.isBlank()) View.GONE else View.VISIBLE
        }

        view.findViewById<TextView>(R.id.tvReviewMedia).apply {
            text = if (review.mediaCount > 0) "${review.mediaCount} photos" else ""
            visibility = if (review.mediaCount > 0) View.VISIBLE else View.GONE
        }

        val stars = view.findViewById<LinearLayout>(R.id.llReviewStars)
        stars.removeAllViews()
        stars.addView(StarRatingViews.buildStarRow(requireContext(), review.overallRating, false, 16f))

        ReviewHelpfulBinder.bind(
            view.findViewById(R.id.btnHelpful),
            view.findViewById(R.id.tvHelpfulCount),
            review
        )
    }

    private fun ratingProgressId(rating: Int): Int = when (rating) {
        1 -> R.id.progressRating1
        2 -> R.id.progressRating2
        3 -> R.id.progressRating3
        4 -> R.id.progressRating4
        else -> R.id.progressRating5
    }

    private fun ratingCountId(rating: Int): Int = when (rating) {
        1 -> R.id.tvRatingCount1
        2 -> R.id.tvRatingCount2
        3 -> R.id.tvRatingCount3
        4 -> R.id.tvRatingCount4
        else -> R.id.tvRatingCount5
    }

    private fun showMissingSpot(view: View) {
        view.findViewById<TextView>(R.id.tvSpotName).text = "Study spot unavailable"
        view.findViewById<View>(R.id.detailPanel).visibility = View.GONE
    }
}
