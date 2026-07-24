package com.studypin.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Uploads and downloads privacy-filtered study-spot photos. */
object StorageRepository {
    private const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
    private val storage = FirebaseStorage.getInstance()
    private val compressionExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun uploadSpotPhoto(
        spotId: String,
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onError(IllegalStateException("You must be signed in to upload a photo"))
            return
        }

        compressionExecutor.execute {
            try {
                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    throw IllegalStateException("Could not encode the selected photo")
                }
                val bytes = output.toByteArray()
                val photoRef = storage.reference
                    .child("studySpotImages/$userId/$spotId.jpg")
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .build()

                photoRef.putBytes(bytes, metadata)
                    .continueWithTask { photoRef.downloadUrl }
                    .addOnSuccessListener { downloadUri ->
                        mainHandler.post { onSuccess(downloadUri.toString()) }
                    }
                    .addOnFailureListener { error ->
                        mainHandler.post { onError(error) }
                    }
            } catch (error: Exception) {
                mainHandler.post { onError(error) }
            }
        }
    }

    fun deleteSpotPhoto(imageUrl: String, onComplete: () -> Unit = {}) {
        try {
            storage.getReferenceFromUrl(imageUrl).delete()
                .addOnCompleteListener { mainHandler.post(onComplete) }
        } catch (_: Exception) {
            mainHandler.post(onComplete)
        }
    }

    fun loadSpotPhoto(
        imageUrl: String?,
        onSuccess: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (imageUrl.isNullOrBlank()) {
            onError(IllegalArgumentException("No photo URL was saved for this spot"))
            return
        }

        try {
            storage.getReferenceFromUrl(imageUrl).getBytes(MAX_DOWNLOAD_BYTES)
                .addOnSuccessListener { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        mainHandler.post {
                            onError(IllegalStateException("The saved photo could not be decoded"))
                        }
                    } else {
                        mainHandler.post { onSuccess(bitmap) }
                    }
                }
                .addOnFailureListener { error -> mainHandler.post { onError(error) } }
        } catch (error: Exception) {
            mainHandler.post { onError(error) }
        }
    }
}
