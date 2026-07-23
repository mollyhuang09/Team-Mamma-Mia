package com.studypin.app.ui.add

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot
import com.studypin.app.utils.LocationUtils

class AddSpotFragment : Fragment() {

    private val simulatedLat = 43.4700
    private val simulatedLng = -80.5428
    private val radiusCheckMeters = 75.0
    private val validationRadiusMeters = 30.0

    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etAddress: EditText
    private lateinit var etHours: EditText
    private lateinit var cbWifi: CheckBox
    private lateinit var cbOutlets: CheckBox
    private lateinit var cbWashroom: CheckBox
    private lateinit var cbPrinting: CheckBox
    private lateinit var capacitySpinner: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var ivSpotPhoto: ImageView
    private lateinit var btnAddPhoto: Button

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
        etHours = view.findViewById(R.id.etHours)
        cbWifi = view.findViewById(R.id.cbWifiAdd)
        cbOutlets = view.findViewById(R.id.cbOutletsAdd)
        cbWashroom = view.findViewById(R.id.cbWashroom)
        cbPrinting = view.findViewById(R.id.cbPrinting)
        capacitySpinner = view.findViewById(R.id.capacitySpinner)
        btnSubmit = view.findViewById(R.id.btnSubmitSpot)
        ivSpotPhoto = view.findViewById(R.id.ivSpotPhoto)
        btnAddPhoto = view.findViewById(R.id.btnAddPhoto)

        capacitySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            Capacity.values().map { it.label }
        )

        btnAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSubmit.setOnClickListener { onSubmitClicked() }
    }

    private fun processImage(uri: Uri) {
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
                } else {
                    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

                    for (face in faces) {
                        val box = face.boundingBox
                        val dynamicBlockSize = (minOf(box.width(), box.height()) / 12).coerceAtLeast(8)
                        pixelateRegion(mutableBitmap, box, dynamicBlockSize)
                    }
                    selectedImageBitmap = mutableBitmap
                    ivSpotPhoto.setImageBitmap(mutableBitmap)
                    Toast.makeText(requireContext(), "Faces blurred for privacy", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                selectedImageBitmap = originalBitmap
                ivSpotPhoto.setImageBitmap(originalBitmap)
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

                    for (y in blockY until blockY + blockHeight) {
                        for (x in blockX until blockX + blockWidth) {
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
        val name = etName.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (address.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.address_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedImageBitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.photo_is_required), Toast.LENGTH_SHORT).show()
            return
        }

        // New Logic: Check for unvalidated spots nearby to vouch
        val unvalidatedNearby = findNearbyUnvalidatedSpot()
        if (unvalidatedNearby != null) {
            showVouchPrompt(unvalidatedNearby)
            return
        }

        // Parent/Orphan logic remains for nesting
        val possibleParent = findNearbyTopLevelSpot()
        if (possibleParent != null) {
            showParentPrompt(possibleParent)
            return
        }

        createSpot(parentId = null, isHiddenGem = false) {
            Toast.makeText(requireContext(), "Spot added for validation!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findNearbyUnvalidatedSpot(): StudySpot? {
        // In a real app, this would query the DB for isValidated=false spots nearby
        return MockData.topLevelSpots().firstOrNull { existing ->
            !existing.isValidated && LocationUtils.distanceInMeters(
                simulatedLat, simulatedLng,
                existing.latitude, existing.longitude
            ) <= validationRadiusMeters
        }
    }

    private fun showVouchPrompt(spot: StudySpot) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.vouch_prompt_title))
            .setMessage(getString(R.string.vouch_prompt_message, spot.name))
            .setPositiveButton(getString(R.string.vouch_yes)) { _, _ ->
                // TODO: Update spot.requestCount and set isValidated=true if count >= 2
                Toast.makeText(requireContext(), getString(R.string.spot_vouched), Toast.LENGTH_SHORT).show()
                clearForm()
            }
            .setNegativeButton(getString(R.string.vouch_no)) { _, _ ->
                createSpot(parentId = null, isHiddenGem = false) {
                    Toast.makeText(requireContext(), "New spot added!", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun findNearbyTopLevelSpot(): StudySpot? {
        return MockData.topLevelSpots().firstOrNull { existing ->
            existing.isValidated && LocationUtils.distanceInMeters(
                simulatedLat, simulatedLng,
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
        if (cbWifi.isChecked) amenities.add("wifi")
        if (cbOutlets.isChecked) amenities.add("outlets")
        if (cbWashroom.isChecked) amenities.add("washroom")
        if (cbPrinting.isChecked) amenities.add("printing")

        val selectedCapacity = Capacity.values()[capacitySpinner.selectedItemPosition]

        val newSpot = StudySpot(
            id = "spot_local_${System.currentTimeMillis()}",
            name = etName.text.toString().trim(),
            description = etDescription.text.toString().trim(),
            address = etAddress.text.toString().trim(),
            latitude = simulatedLat,
            longitude = simulatedLng,
            amenities = amenities,
            hours = etHours.text.toString().trim(),
            createdBy = "device_local_user",
            avgRating = 0.0,
            totalRatings = 0,
            capacity = selectedCapacity,
            currentCheckIns = 0,
            parentSpotId = parentId,
            isHiddenGem = isHiddenGem,
            imageUrl = "placeholder_uri", // In real app, upload bitmap to storage first
            isValidated = false,
            requestCount = 1
        )

        onComplete(newSpot)
        clearForm()
    }

    private fun clearForm() {
        etName.text.clear()
        etDescription.text.clear()
        etAddress.text.clear()
        etHours.text.clear()
        cbWifi.isChecked = false
        cbOutlets.isChecked = false
        cbWashroom.isChecked = false
        cbPrinting.isChecked = false
        capacitySpinner.setSelection(0)
        ivSpotPhoto.setImageDrawable(null)
        selectedImageBitmap = null
    }
}