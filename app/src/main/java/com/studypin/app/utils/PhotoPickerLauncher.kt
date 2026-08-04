package com.studypin.app.utils

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studypin.app.ui.showMessage
import java.io.File

/**
 * Lets the user choose between taking a photo now or picking one from the gallery.
 * Must be constructed as a field of the owning Fragment so the activity-result
 * launchers register before the fragment reaches CREATED state.
 */
class PhotoPickerLauncher(
    private val fragment: Fragment,
    private val onImagePicked: (Uri) -> Unit
) {
    private var pendingCameraUri: Uri? = null

    private val pickImageLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(onImagePicked)
    }

    private val takePictureLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let(onImagePicked)
        }
    }

    private val cameraPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            fragment.context?.showMessage("Camera permission is required to take a photo")
        }
    }

    fun showChooser() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Add Photo")
            .setItems(arrayOf<CharSequence>("Take Photo", "Choose from Gallery")) { _, index ->
                if (index == 0) requestCameraAndLaunch() else pickImageLauncher.launch("image/*")
            }
            .show()
    }

    private fun requestCameraAndLaunch() {
        val context = fragment.requireContext()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val context = fragment.requireContext()
        val photoDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File.createTempFile("spot_photo_", ".jpg", photoDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }
}
