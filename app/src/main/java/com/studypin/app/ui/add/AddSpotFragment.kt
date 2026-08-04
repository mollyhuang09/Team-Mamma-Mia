package com.studypin.app.ui.add

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.studypin.app.R
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot
import com.studypin.app.utils.ImageCaptureHelper
import com.studypin.app.utils.LocationUtils
import com.studypin.app.utils.PhotoPickerLauncher
import com.studypin.app.ui.showMessage
import java.util.Locale

class AddSpotFragment : Fragment() {

    private var pickedLat = 43.4723
    private var pickedLng = -80.5449
    private var locationExplicitlySet = false
    /** Shared radius for both the vouch and hidden-gem nearby checks, so there's no gap between them. */
    private val radiusCheckMeters = 75.0

    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etAddress: EditText
    private lateinit var etOpeningTime: EditText
    private lateinit var etClosingTime: EditText
    private lateinit var dropdownCategory: AutoCompleteTextView
    private lateinit var dropdownCapacity: AutoCompleteTextView
    private lateinit var chipGroupAmenities: ChipGroup
    private lateinit var btnSubmit: Button
    private lateinit var ivSpotPhoto: ImageView
    private lateinit var btnAddPhoto: Button
    private lateinit var layoutPhotoPlaceholder: View

    private var selectedImageBitmap: Bitmap? = null

    private val photoPickerLauncher = PhotoPickerLauncher(this) { uri -> processImage(uri) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_spot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etName = view.findViewById(R.id.etSpotName)
        etDescription = view.findViewById(R.id.etDescription)
        etAddress = view.findViewById(R.id.etAddress)
        etOpeningTime = view.findViewById(R.id.etOpeningTime)
        etClosingTime = view.findViewById(R.id.etClosingTime)
        dropdownCategory = view.findViewById(R.id.dropdownCategory)
        dropdownCapacity = view.findViewById(R.id.dropdownCapacity)
        chipGroupAmenities = view.findViewById(R.id.chipGroupAmenities)
        btnSubmit = view.findViewById(R.id.btnSubmitSpot)
        ivSpotPhoto = view.findViewById(R.id.ivSpotPhoto)
        btnAddPhoto = view.findViewById(R.id.btnAddPhoto)
        layoutPhotoPlaceholder = view.findViewById(R.id.layoutPhotoPlaceholder)

        setupDropdowns()
        setupTimePickers()
        setupLocationPicker()

        btnAddPhoto.setOnClickListener {
            photoPickerLauncher.showChooser()
        }

        btnSubmit.setOnClickListener { onSubmitClicked() }
    }

    private fun setupDropdowns() {
        val categories = listOf("Library", "Cafe", "Lobby", "Study Room", "Outdoor", "Lab")
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)
        dropdownCategory.setAdapter(categoryAdapter)

        val capacities = Capacity.values().map { it.label }
        val capacityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, capacities)
        dropdownCapacity.setAdapter(capacityAdapter)
    }

    private fun setupTimePickers() {
        etOpeningTime.setOnClickListener { showTimePicker(etOpeningTime) }
        etClosingTime.setOnClickListener { showTimePicker(etClosingTime) }
    }

    private fun showTimePicker(targetEditText: EditText) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(9)
            .setMinute(0)
            .setTitleText("Select Time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour == 0 || hour == 12) 12 else hour % 12
            val formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, amPm)
            targetEditText.setText(formattedTime)
        }

        picker.show(childFragmentManager, "time_picker")
    }

    private fun setupLocationPicker() {
        view?.findViewById<Button>(R.id.btnPinOnMap)?.setOnClickListener {
            val args = if (locationExplicitlySet) {
                bundleOf(
                    LocationPickerFragment.KEY_LATITUDE to pickedLat,
                    LocationPickerFragment.KEY_LONGITUDE to pickedLng
                )
            } else {
                null
            }
            findNavController().navigate(R.id.action_addSpot_to_locationPicker, args)
        }

        setFragmentResultListener(LocationPickerFragment.RESULT_KEY) { _, bundle ->
            pickedLat = bundle.getDouble(LocationPickerFragment.KEY_LATITUDE)
            pickedLng = bundle.getDouble(LocationPickerFragment.KEY_LONGITUDE)
            locationExplicitlySet = true
            val address = bundle.getString(LocationPickerFragment.KEY_ADDRESS)

            if (!address.isNullOrBlank()) {
                etAddress.setText(address)
            }
            view?.findViewById<Button>(R.id.btnPinOnMap)?.setText(R.string.location_set)
            showMessage("Location pinned!")
        }
    }

    private fun processImage(uri: Uri) {
        ImageCaptureHelper.processPickedImage(
            context = requireContext(),
            uri = uri,
            onProcessed = { bitmap, facesBlurred ->
                selectedImageBitmap = bitmap
                ivSpotPhoto.setImageBitmap(bitmap)
                ivSpotPhoto.visibility = View.VISIBLE
                layoutPhotoPlaceholder.visibility = View.GONE
                if (facesBlurred) {
                    showMessage("Faces blurred for privacy")
                }
            },
            onFailure = {
                showMessage("Could not load photo")
            }
        )
    }

    private fun onSubmitClicked() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            showMessage("Sign in to add a study spot")
            return
        }

        val name = etName.text.toString().trim()

        if (name.isEmpty()) {
            showMessage(getString(R.string.name_required))
            return
        }
        if (!locationExplicitlySet) {
            showMessage(getString(R.string.location_not_set))
            return
        }
        if (selectedImageBitmap == null) {
            showMessage(getString(R.string.photo_is_required))
            return
        }

        btnSubmit.isEnabled = false
        checkNearbySpotsThenSubmit()
    }

    private fun checkNearbySpotsThenSubmit() {
        StudySpotRepository.getSpots(
            onSuccess = { spots ->
                if (!isAdded) return@getSpots
                val unvalidatedNearby = findNearbyUnvalidatedSpot(spots)
                val validatedNearby = findNearbyTopLevelSpot(spots)

                when {
                    unvalidatedNearby != null && validatedNearby != null ->
                        showCombinedPrompt(unvalidatedNearby, validatedNearby)
                    unvalidatedNearby != null -> showVouchPrompt(unvalidatedNearby)
                    validatedNearby != null -> showParentPrompt(validatedNearby)
                    else -> createSpot(parentId = null, isHiddenGem = false) {
                        showMessage("Spot added for validation!")
                    }
                }
            },
            onError = { error ->
                if (isAdded) {
                    btnSubmit.isEnabled = true
                    showMessage("Could not check nearby spots: ${error.message}", long = true)
                }
            }
        )
    }

    /** Nearest spot (top-level or hidden gem) within [radiusMeters] matching [predicate], or null. */
    private fun findNearestSpot(
        spots: List<StudySpot>,
        radiusMeters: Double,
        predicate: (StudySpot) -> Boolean
    ): StudySpot? {
        return spots.asSequence()
            .filter(predicate)
            .map { it to LocationUtils.distanceInMeters(pickedLat, pickedLng, it.latitude, it.longitude) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    private fun findNearbyUnvalidatedSpot(spots: List<StudySpot>): StudySpot? =
        findNearestSpot(spots, radiusCheckMeters) { !it.isValidated }

    private fun showVouchPrompt(spot: StudySpot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.vouch_prompt_title))
            .setMessage(getString(R.string.vouch_prompt_message, spot.name))
            .setPositiveButton(getString(R.string.vouch_yes)) { _, _ -> vouchFor(spot) }
            .setNeutralButton("Hidden gem inside \"${spot.name}\"") { _, _ -> createAsHiddenGem(spot) }
            .setNegativeButton(getString(R.string.vouch_no)) { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    showMessage("New spot added!")
                }
            }
            .show()
    }

    private fun vouchFor(spot: StudySpot) {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            btnSubmit.isEnabled = true
            showMessage("Sign in to vouch for a spot")
            return
        }
        StudySpotRepository.vouchSpot(spot.id, userId).let { task ->
            task.addOnSuccessListener {
                showMessage(getString(R.string.spot_vouched))
                clearForm()
            }
            task.addOnFailureListener { error ->
                showMessage(error.message ?: "Could not save your vouch", long = true)
            }
            task.addOnCompleteListener { btnSubmit.isEnabled = true }
        }
    }

    private fun findNearbyTopLevelSpot(spots: List<StudySpot>): StudySpot? =
        findNearestSpot(spots, radiusCheckMeters) { it.isValidated }

    private fun showParentPrompt(parent: StudySpot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hidden gem detected")
            .setMessage("This looks like it's inside \"${parent.name}\". Is this a hidden spot within that location?")
            .setPositiveButton("Yes, it's a hidden gem") { _, _ -> createAsHiddenGem(parent) }
            .setNegativeButton("No, it's separate") { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    showMessage("Spot added!")
                }
            }
            .show()
    }

    private fun createAsHiddenGem(parent: StudySpot) {
        // Attach under the existing top-level location even if `parent` is itself a hidden
        // gem, so we never create more than one level of nesting.
        val topLevelParentId = parent.parentSpotId ?: parent.id
        createSpot(parentId = topLevelParentId, isHiddenGem = true) {
            showMessage("Added as a hidden gem inside ${parent.name}!")
        }
    }

    private fun showCombinedPrompt(unvalidated: StudySpot, validated: StudySpot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Nearby spots found")
            .setMessage(
                "We found \"${unvalidated.name}\" (not yet verified) and " +
                    "\"${validated.name}\" (verified) nearby. What would you like to do?"
            )
            .setPositiveButton("Vouch for \"${unvalidated.name}\"") { _, _ -> vouchFor(unvalidated) }
            .setNeutralButton("Hidden gem inside \"${validated.name}\"") { _, _ -> createAsHiddenGem(validated) }
            .setNegativeButton("No, it's separate") { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    showMessage("Spot added!")
                }
            }
            .show()
    }

    private fun createSpot(
        parentId: String?,
        isHiddenGem: Boolean,
        onComplete: (StudySpot) -> Unit
    ) {
        val amenities = mutableListOf<String>()
        if (view?.findViewById<Chip>(R.id.chipWifi)?.isChecked == true) amenities.add("wifi")
        if (view?.findViewById<Chip>(R.id.chipOutlets)?.isChecked == true) amenities.add("outlets")
        if (view?.findViewById<Chip>(R.id.chipWashroom)?.isChecked == true) amenities.add("washroom")
        if (view?.findViewById<Chip>(R.id.chipPrinting)?.isChecked == true) amenities.add("printing")

        val capacityLabel = dropdownCapacity.text.toString()
        val selectedCapacity = Capacity.values().firstOrNull { it.label == capacityLabel } ?: Capacity.SMALL

        val category = dropdownCategory.text.toString()

        val openTime = etOpeningTime.text.toString()
        val closeTime = etClosingTime.text.toString()
        val hours = if (openTime.isNotBlank() && closeTime.isNotBlank()) "$openTime - $closeTime" else ""

        val spotId = "spot_local_${System.currentTimeMillis()}"


        btnSubmit.isEnabled = false
        showMessage("Uploading spot...")

        selectedImageBitmap?.let { bitmap ->
            StudySpotRepository.uploadSpotImage(
                spotId = spotId,
                bitmap = bitmap,
                onSuccess = { downloadUrl ->
                    val newSpot = StudySpot(
                        id = spotId,
                        name = etName.text.toString().trim(),
                        description = etDescription.text.toString().trim(),
                        address = etAddress.text.toString().trim(),
                        latitude = pickedLat,
                        longitude = pickedLng,
                        amenities = amenities,
                        hours = hours,
                        createdBy = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            ?: "anonymous",
                        createdAt = System.currentTimeMillis(),
                        avgRating = 0.0,
                        totalRatings = 0,
                        capacity = selectedCapacity,
                        currentCheckIns = 0,
                        parentSpotId = parentId,
                        isHiddenGem = isHiddenGem,
                        imageUrl = downloadUrl,
                        imageUrls = listOf(downloadUrl),
                        isValidated = false,
                        requestCount = 1,
                        category = category
                    )

                    StudySpotRepository.addSpot(newSpot)
                        .addOnSuccessListener {
                            onComplete(newSpot)
                            clearForm()
                        }
                        .addOnFailureListener { error ->
                            showMessage("Could not save spot: ${error.message}", long = true)
                        }
                        .addOnCompleteListener {
                            btnSubmit.isEnabled = true
                        }
                },
                onError = { error ->
                    btnSubmit.isEnabled = true
                    showMessage("Image upload failed: ${error.message}", long = true)
                }
            )
        } ?: run {
            btnSubmit.isEnabled = true
            showMessage("Please select an image")
        }
    }

    private fun clearForm() {
        etName.text.clear()
        etDescription.text.clear()
        etAddress.text.clear()
        etOpeningTime.text.clear()
        etClosingTime.text.clear()
        dropdownCategory.text.clear()
        dropdownCapacity.text.clear()
        view?.findViewById<Button>(R.id.btnPinOnMap)?.setText(R.string.pin_location_on_map)
        chipGroupAmenities.clearCheck()
        ivSpotPhoto.setImageDrawable(null)
        ivSpotPhoto.visibility = View.GONE
        layoutPhotoPlaceholder.visibility = View.VISIBLE
        selectedImageBitmap = null
    }
}
