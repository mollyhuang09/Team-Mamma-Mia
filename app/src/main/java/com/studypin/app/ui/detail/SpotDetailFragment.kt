package com.studypin.app.ui.detail

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.core.view.updateLayoutParams
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.LoginActivity
import com.studypin.app.R
import com.studypin.app.data.OccupancyRepository
import com.studypin.app.data.ReviewRepository
import com.studypin.app.data.SavedSpotRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.location.LocationReminderManager
import com.studypin.app.model.StudySpot
import com.studypin.app.model.StudySpotReview
import com.studypin.app.model.SpotStatus
import com.studypin.app.model.isInactive
import com.studypin.app.ui.review.ReviewHelpfulBinder
import com.studypin.app.ui.review.StarRatingViews
import com.studypin.app.ui.setOnApplyStatusBarInsetsListener
import com.studypin.app.ui.showPhotoUploadPlaceholder
import com.studypin.app.utils.ImageUtils
import com.studypin.app.ui.toTagLabel
import com.studypin.app.utils.LocationUtils
import com.studypin.app.ui.showMessage
import java.util.Locale

class SpotDetailFragment : Fragment() {
    private var spotListener: ListenerRegistration? = null
    private var checkInListener: ListenerRegistration? = null
    private var currentSpot: StudySpot? = null
    private var statusBarHeightPx: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spot_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString("spotId") ?: ""
        setupCheckInButton(view, spotId)
        setupTrackVisitButton(view)
        applyStatusBarInsets(view)

        spotListener = StudySpotRepository.observeSpot(
            spotId = spotId,
            onSuccess = { spot ->
                if (!isAdded) return@observeSpot
                currentSpot = spot
                if (spot == null) {
                    showMissingSpot(view)
                } else {
                    bindLoadedSpot(view, spotId, spot)
                    updateTrackVisitButton(view.findViewById(R.id.btnTrackVisit), spot)
                }
            },
            onError = {
                if (isAdded) showMissingSpot(view)
            }
        )
    }

    private fun bindLoadedSpot(view: View, spotId: String, spot: StudySpot) {
        bindSpot(view, spot)
        bindInactiveSpotState(view, spot)
        bindVerificationStatus(view, spot)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnReport).setOnClickListener {
            navigateToReport(spotId, spot.name)
        }

        view.findViewById<View>(R.id.btnShare).setOnClickListener {
            showMessage("Sharing will be connected later")
        }
        view.findViewById<View>(R.id.btnEdit).setOnClickListener {
            findNavController().navigate(R.id.action_spotDetail_to_editSpot)
        }
        view.findViewById<View>(R.id.btnDirection).setOnClickListener {
            openDirections(spot)
        }
        val saveButton = view.findViewById<MaterialButton>(R.id.btnSave)
        val savedUid = FirebaseAuth.getInstance().currentUser?.uid
        if (savedUid != null) {
            SavedSpotRepository.isSpotSaved(savedUid, spotId) { saved ->
                if (isAdded) updateSaveButtonUi(saveButton, saved)
            }
        }
        saveButton.setOnClickListener { button ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                showMessage("Sign in to save spots")
                return@setOnClickListener
            }
            val goingToSaved = !(button as MaterialButton).isSelected
            if (goingToSaved) {
                SavedSpotRepository.saveSpot(uid, spotId) { success, error ->
                    if (!isAdded) return@saveSpot
                    if (success) {
                        updateSaveButtonUi(button, true)
                    } else {
                        showMessage(error ?: "Could not save spot", long = true)
                    }
                }
            } else {
                SavedSpotRepository.unsaveSpot(uid, spotId) { success, error ->
                    if (!isAdded) return@unsaveSpot
                    if (success) {
                        updateSaveButtonUi(button, false)
                    } else {
                        showMessage(error ?: "Could not remove spot", long = true)
                    }
                }
            }
        }
        view.findViewById<View>(R.id.btnAddPhoto).setOnClickListener {
            requireContext().showPhotoUploadPlaceholder()
        }

        view.findViewById<View>(R.id.btnAddReview).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
        }

        view.findViewById<View>(R.id.tvRatingDetail).setOnClickListener {
            val bundle = Bundle().apply { putString("spotId", spotId) }
            findNavController().navigate(R.id.action_spotDetail_to_reviewList, bundle)
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (spot.status.isInactive() ||
            ReviewRepository.hasUserReviewedSpot("You", spotId) ||
            (currentUserId != null && ReviewRepository.hasUserReviewedSpot(currentUserId, spotId))
        ) {
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

    private fun openDirections(spot: StudySpot) {
        val mapsUri = Uri.parse("google.navigation:q=${spot.latitude},${spot.longitude}")
        val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            startActivity(mapsIntent)
        } catch (_: ActivityNotFoundException) {
            val browserUri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1&destination=" +
                    "${spot.latitude},${spot.longitude}"
            )
            try {
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            } catch (_: ActivityNotFoundException) {
                showMessage("No maps app is available")
            }
        }
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
        val context = requireContext()
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocationPermission || !hasBackgroundLocationPermission) {
            showTrackingMessage("Enable location permissions in Settings before tracking a visit")
            return
        }

        val cancellationTokenSource = CancellationTokenSource()
        try {
            LocationServices.getFusedLocationProviderClient(context)
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
        } catch (_: SecurityException) {
            showTrackingMessage("Enable location permissions in Settings before tracking a visit")
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
        if (isAdded) showMessage(message, long = true)
    }

    private fun applyStatusBarInsets(view: View) {
        val heroContainer = view.findViewById<View>(R.id.heroContainer)
        val backButton = view.findViewById<View>(R.id.btnBack)
        val heroActions = view.findViewById<View>(R.id.heroActions)

        heroContainer.setOnApplyStatusBarInsetsListener { statusBarHeight ->
            statusBarHeightPx = statusBarHeight
            val topMargin = statusBarHeight + dpToPx(4)

            backButton.updateLayoutParams<FrameLayout.LayoutParams> {
                this.topMargin = topMargin
            }
            heroActions.updateLayoutParams<FrameLayout.LayoutParams> {
                this.topMargin = topMargin
            }

            if (heroImageIsHidden(heroContainer)) {
                heroContainer.updateLayoutParams<LinearLayout.LayoutParams> {
                    height = compactHeroHeightPx()
                }
            }

        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun compactHeroHeightPx(): Int =
        statusBarHeightPx + dpToPx(16 + 48 + 8)

    private fun heroImageIsHidden(heroContainer: View): Boolean =
        heroContainer.findViewById<ImageView>(R.id.ivSpotHero).visibility == View.GONE

    private fun bindInactiveSpotState(view: View, spot: StudySpot) {
        val inactive = spot.status.isInactive()
        val actionButtonIds = listOf(
            R.id.btnDirection,
            R.id.btnSave,
            R.id.btnAddPhoto
        )

        actionButtonIds.forEach { buttonId ->
            view.findViewById<MaterialButton>(buttonId).apply {
                isEnabled = !inactive
                alpha = if (inactive) 0.5f else 1f
            }
        }

        view.findViewById<View>(R.id.layoutSpotInactiveInfo).visibility =
            if (inactive) View.VISIBLE else View.GONE

        if (!inactive) return

        view.findViewById<TextView>(R.id.tvSpotInactiveReasonTitle).setText(
            when (spot.status) {
                SpotStatus.TEMPORARILY_CLOSED -> R.string.preview_closed_reason_title
                else -> R.string.preview_inactive_reason_title
            }
        )
        view.findViewById<TextView>(R.id.tvSpotInactiveReason).setText(
            when (spot.status) {
                SpotStatus.TEMPORARILY_CLOSED -> R.string.preview_closed_reason
                else -> R.string.preview_inactive_reason
            }
        )
        view.findViewById<TextView>(R.id.tvSpotLastVerifiedDate).text =
            spot.lastVerifiedLabel.ifBlank {
                getString(R.string.preview_last_verified_unknown)
            }
    }

    private fun updateSaveButtonUi(saveButton: MaterialButton, isSaved: Boolean) {
        saveButton.isSelected = isSaved
        saveButton.text = if (isSaved) "Saved" else "Save"
        saveButton.setIconResource(
            if (isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )
        saveButton.setIconTintResource(R.color.brand_main)
        saveButton.backgroundTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (isSaved) R.color.surface_light_tint else R.color.surface_color
        )
        saveButton.contentDescription = if (isSaved) {
            "Remove spot from saved places"
        } else {
            "Save spot"
        }
    }

    private fun bindVerificationStatus(view: View, spot: StudySpot) {
        val tvStatus = view.findViewById<TextView>(R.id.tvVerificationStatus)
        val btnVerify = view.findViewById<MaterialButton>(R.id.btnVerify)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (spot.isValidated) {
            tvStatus.text = getString(R.string.spot_detail_verified_line)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.verified_green))
            btnVerify.visibility = View.GONE
        } else {
            val remaining = (StudySpotRepository.REQUIRED_VOUCHES - spot.requestCount).coerceAtLeast(1)
            tvStatus.text = getString(R.string.spot_detail_needs_confirmation, remaining)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.pending_amber))

            val canVerify = currentUserId != null &&
                spot.createdBy != currentUserId &&
                !spot.vouchedBy.contains(currentUserId)
            btnVerify.visibility = if (canVerify) View.VISIBLE else View.GONE
        }

        btnVerify.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                showMessage("Sign in to verify this spot")
                return@setOnClickListener
            }
            val bundle = Bundle().apply {
                putString("spotId", spot.id)
                putBoolean("verifyMode", true)
            }
            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
        }
    }

    private fun setupCheckInButton(view: View, spotId: String) {
        val button = view.findViewById<MaterialButton>(R.id.btnCheckInToggle)
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            styleSignedOutCheckInButton(button)
            button.text = "Sign in to check in"
            button.isEnabled = true
            button.setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
            return
        }

        if (spotId.isBlank()) {
            styleUnavailableCheckInButton(button)
            button.text = "Check-in unavailable"
            button.isEnabled = false
            return
        }

        styleSignedInCheckInButton(button)
        button.isEnabled = false
        checkInListener = OccupancyRepository.observeCheckIn(
            spotId = spotId,
            userId = userId,
            onSuccess = { checkedIn ->
                if (!isAdded) return@observeCheckIn
                button.isEnabled = true
                button.isSelected = checkedIn
                button.text = if (checkedIn) "Check Out" else "I'm at this study spot"
            },
            onError = {
                if (isAdded) {
                    styleUnavailableCheckInButton(button)
                    button.isEnabled = false
                    button.text = "Check-in unavailable"
                }
            }
        )

        button.setOnClickListener {
            button.isEnabled = false
            val checkedIn = button.isSelected
            if (checkedIn) {
                LocationReminderManager.endVisit(requireContext(), spotId, userId)
                    ?.addOnSuccessListener {
                        if (isAdded) {
                            showMessage("Checked out")
                            val bundle = Bundle().apply { putString("spotId", spotId) }
                            findNavController().navigate(R.id.action_spotDetail_to_addReview, bundle)
                        }
                    }
                    ?.addOnFailureListener { error ->
                        if (isAdded) {
                            button.isEnabled = true
                            showMessage("Could not update check-in: ${error.message}", long = true)
                        }
                    }
            } else {
                OccupancyRepository.checkIn(spotId, userId)
                    .addOnSuccessListener { previousSpotId ->
                        if (previousSpotId != null) {
                            LocationReminderManager.stopTracking(requireContext(), previousSpotId)
                            if (isAdded) {
                                showMessage("Checked out of your previous spot and checked into this one")
                            }
                        }
                        currentSpot?.let { spot ->
                            if (!LocationReminderManager.isTracking(requireContext(), spot.id)) {
                                LocationReminderManager.startTracking(
                                    requireContext(),
                                    spot,
                                    onSuccess = {},
                                    onFailure = {}
                                )
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        if (isAdded) {
                            button.isEnabled = true
                            showMessage("Could not update check-in: ${error.message}", long = true)
                        }
                    }
            }
        }
    }

    private fun styleSignedOutCheckInButton(button: MaterialButton) {
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        button.strokeWidth = 0
        button.cornerRadius = 0
        button.elevation = 0f
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_main))
    }

    private fun styleSignedInCheckInButton(button: MaterialButton) {
        button.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.brand_main)
        button.strokeWidth = 0
        button.cornerRadius = dpToPx(10)
        button.elevation = dpToPx(1).toFloat()
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.surface_color))
    }

    private fun styleUnavailableCheckInButton(button: MaterialButton) {
        button.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.button_secondary)
        button.strokeColor =
            ContextCompat.getColorStateList(requireContext(), R.color.border_default)
        button.strokeWidth = dpToPx(1)
        button.cornerRadius = dpToPx(10)
        button.elevation = 0f
        button.rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.surface_muted))
    }

    private fun bindSpot(view: View, spot: StudySpot) {
        view.findViewById<TextView>(R.id.tvSpotName).text = spot.name
        view.findViewById<TextView>(R.id.tvAvailability).text = spot.occupancyLabel()
        view.findViewById<TextView>(R.id.tvOccupancyDetail).text =
            spot.occupancySummary()

        val stats = ReviewRepository.displayStatsForSpot(spot)
        view.findViewById<TextView>(R.id.tvRatingDetail).text = String.format(
            Locale.CANADA, "★ %.1f / 5 (%d rating%s)", stats.averageOverall, stats.reviewCount,
            if (stats.reviewCount == 1) "" else "s"
        )

        view.findViewById<TextView>(R.id.tvDescription).text = spot.description
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

        photoGalleryContainer.removeAllViews()

        if (imageUris.isEmpty()) {
            heroImage.visibility = View.GONE
            photoHeader.visibility = View.GONE
            photoGallery.visibility = View.GONE

            heroContainer.updateLayoutParams<LinearLayout.LayoutParams> {
                height = compactHeroHeightPx()
            }
            detailPanel.layoutParams = detailPanel.layoutParams.apply {
                if (this is LinearLayout.LayoutParams) topMargin = 0
            }
            return
        }

        val firstImageUri = imageUris.first()
        heroImage.visibility = View.VISIBLE
        heroImage.load(ImageUtils.toLoadableModel(firstImageUri)) {
            placeholder(R.drawable.photo_placeholder)
            crossfade(true)
        }

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
                load(ImageUtils.toLoadableModel(uri)) {
                    placeholder(R.drawable.photo_placeholder)
                    crossfade(true)
                }
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
        val reviewSummary = view.findViewById<View>(R.id.layoutReviewSummary)
        val emptyReviews = view.findViewById<TextView>(R.id.tvEmptyReviews)
        val reviews = ReviewRepository.reviewsForSpot(spot.id)

        reviewContainer.removeAllViews()
        reviewSummary.visibility = if (reviews.isEmpty()) View.GONE else View.VISIBLE
        emptyReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.btnViewMoreReviews).apply {
            visibility = if (reviews.size > 3) View.VISIBLE else View.GONE
            setOnClickListener {
                val bundle = Bundle().apply { putString("spotId", spot.id) }
                findNavController().navigate(R.id.action_spotDetail_to_reviewList, bundle)
            }
        }

        reviews.take(3).forEach { review ->
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
        view.findViewById<TextView>(R.id.tvReviewText).apply {
            text = review.reviewText
            visibility = if (review.reviewText.isBlank()) View.GONE else View.VISIBLE
        }

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
