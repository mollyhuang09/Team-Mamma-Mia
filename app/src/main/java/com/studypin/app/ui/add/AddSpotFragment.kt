package com.studypin.app.ui.add

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.studypin.app.R
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot
import com.studypin.app.utils.LocationUtils
import java.util.Locale

class AddSpotFragment : Fragment() {

    private var pickedLat = 43.4723
    private var pickedLng = -80.5449
    private var locationExplicitlySet = false
    private val radiusCheckMeters = 75.0
    private val validationRadiusMeters = 30.0

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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImage(it) }
    }

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
            pickImageLauncher.launch("image/*")
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
            Toast.makeText(requireContext(), "Location pinned!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImage(uri: Uri) {
        @Suppress("DEPRECATION")
        val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        applyPrivacyFilter(correctOrientation(bitmap, uri))
    }

    private fun correctOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val degrees = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyPrivacyFilter(originalBitmap: Bitmap) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(originalBitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    selectedImageBitmap = originalBitmap
                    ivSpotPhoto.setImageBitmap(originalBitmap)
                    ivSpotPhoto.visibility = View.VISIBLE
                    layoutPhotoPlaceholder.visibility = View.GONE
                } else {
                    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

                    for (face in faces) {
                        val box = face.boundingBox
                        val dynamicBlockSize = (minOf(box.width(), box.height()) / 12).coerceAtLeast(8)
                        pixelateRegion(mutableBitmap, box, dynamicBlockSize)
                    }
                    selectedImageBitmap = mutableBitmap
                    ivSpotPhoto.setImageBitmap(mutableBitmap)
                    ivSpotPhoto.visibility = View.VISIBLE
                    layoutPhotoPlaceholder.visibility = View.GONE
                    Toast.makeText(requireContext(), "Faces blurred for privacy", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                selectedImageBitmap = originalBitmap
                ivSpotPhoto.setImageBitmap(originalBitmap)
                ivSpotPhoto.visibility = View.VISIBLE
                layoutPhotoPlaceholder.visibility = View.GONE
                Toast.makeText(requireContext(), "Privacy filter failed, using original", Toast.LENGTH_SHORT).show()
            }
    }

    private fun pixelateRegion(bitmap: Bitmap, region: Rect, blockSize: Int = 16) {
        val left = region.left.coerceIn(0, bitmap.width)
        val top = region.top.coerceIn(0, bitmap.height)
        val right = region.right.coerceIn(0, bitmap.width)
        val bottom = region.bottom.coerceIn(0, bitmap.height)

        var blockY = top
        while (blockY < bottom) {
            val blockHeight = minOf(blockSize, bottom - blockY)
            var blockX = left
            while (blockX < right) {
                val blockWidth = minOf(blockSize, right - blockX)

                var redSum = 0L
                var greenSum = 0L
                var blueSum = 0L
                var pixelCount = 0
                for (y in blockY until blockY + blockHeight) {
                    for (x in blockX until blockX + blockWidth) {
                        val pixel = bitmap.getPixel(x, y)
                        redSum += (pixel shr 16) and 0xFF
                        greenSum += (pixel shr 8) and 0xFF
                        blueSum += pixel and 0xFF
                        pixelCount++
                    }
                }

                if (pixelCount > 0) {
                    val avgRed = (redSum / pixelCount).toInt()
                    val avgGreen = (greenSum / pixelCount).toInt()
                    val avgBlue = (blueSum / pixelCount).toInt()
                    val avgColor = (0xFF shl 24) or (avgRed shl 16) or (avgGreen shl 8) or avgBlue

                    for (y in blockY until (blockY + blockHeight)) {
                        for (x in blockX until (blockX + blockWidth)) {
                            bitmap.setPixel(x, y, avgColor)
                        }
                    }
                }

                blockX += blockSize
            }
            blockY += blockSize
        }
    }

    private fun onSubmitClicked() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(requireContext(), "Sign in to add a study spot", Toast.LENGTH_SHORT).show()
            return
        }

        val name = etName.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (!locationExplicitlySet) {
            Toast.makeText(requireContext(), getString(R.string.location_not_set), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImageBitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.photo_is_required), Toast.LENGTH_SHORT).show()
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
                if (unvalidatedNearby != null) {
                    showVouchPrompt(unvalidatedNearby)
                    return@getSpots
                }

                val possibleParent = findNearbyTopLevelSpot(spots)
                if (possibleParent != null) {
                    showParentPrompt(possibleParent)
                } else {
                    createSpot(parentId = null, isHiddenGem = false) {
                        Toast.makeText(requireContext(), "Spot added for validation!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onError = { error ->
                if (isAdded) {
                    btnSubmit.isEnabled = true
                    Toast.makeText(requireContext(), "Could not check nearby spots: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun findNearbyUnvalidatedSpot(spots: List<StudySpot>): StudySpot? {
        return spots.asSequence().filter { it.parentSpotId == null }.firstOrNull { existing ->
            !existing.isValidated && LocationUtils.distanceInMeters(
                pickedLat, pickedLng,
                existing.latitude, existing.longitude
            ) <= validationRadiusMeters
        }
    }

    private fun showVouchPrompt(spot: StudySpot) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.vouch_prompt_title))
            .setMessage(getString(R.string.vouch_prompt_message, spot.name))
            .setPositiveButton(getString(R.string.vouch_yes)) { _, _ ->
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    btnSubmit.isEnabled = true
                    Toast.makeText(requireContext(), "Sign in to vouch for a spot", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                StudySpotRepository.vouchSpot(spot.id, userId).let { task ->
                    task.addOnSuccessListener {
                        Toast.makeText(requireContext(), getString(R.string.spot_vouched), Toast.LENGTH_SHORT).show()
                        clearForm()
                    }
                    task.addOnFailureListener { error ->
                        Toast.makeText(requireContext(), error.message ?: "Could not save your vouch", Toast.LENGTH_LONG).show()
                    }
                    task.addOnCompleteListener { btnSubmit.isEnabled = true }
                }
            }
            .setNegativeButton(getString(R.string.vouch_no)) { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    Toast.makeText(requireContext(), "New spot added!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun findNearbyTopLevelSpot(spots: List<StudySpot>): StudySpot? {
        return spots.asSequence().filter { it.parentSpotId == null }.firstOrNull { existing ->
            existing.isValidated && LocationUtils.distanceInMeters(
                pickedLat, pickedLng,
                existing.latitude, existing.longitude
            ) <= radiusCheckMeters
        }
    }

    private fun showParentPrompt(parent: StudySpot) {
        AlertDialog.Builder(requireContext())
            .setTitle("Hidden gem detected")
            .setMessage("This looks like it's inside \"${parent.name}\". Is this a hidden spot within that location?")
            .setPositiveButton("Yes, it's a hidden gem") { _, _ ->
                createSpot(parentId = parent.id, isHiddenGem = true) {
                    Toast.makeText(requireContext(), "Added as a hidden gem inside ${parent.name}!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No, it's separate") { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    Toast.makeText(requireContext(), "Spot added!", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), "Uploading spot...", Toast.LENGTH_SHORT).show()

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
                            Toast.makeText(requireContext(), "Could not save spot: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                        .addOnCompleteListener {
                            btnSubmit.isEnabled = true
                        }
                },
                onError = { error ->
                    btnSubmit.isEnabled = true
                    Toast.makeText(requireContext(), "Image upload failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        } ?: run {
            btnSubmit.isEnabled = true
            Toast.makeText(requireContext(), "Please select an image", Toast.LENGTH_SHORT).show()
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
