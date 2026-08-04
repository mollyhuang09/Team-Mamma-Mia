package com.studypin.app.data

import android.graphics.Bitmap
import android.util.Base64
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.model.Capacity
import com.studypin.app.model.SpotStatus
import com.studypin.app.model.StudySpot
import java.io.ByteArrayOutputStream

/** Firestore access for the shared studySpots collection. */
object StudySpotRepository {
    private val spots = FirebaseFirestore.getInstance().collection("studySpots")

    // Firebase Storage requires the paid Blaze plan, so spot photos are instead
    // compressed and embedded directly on the Firestore document as a base64 data
    // URI. This keeps the demo free; Firestore caps a document at 1MB, so the
    // encoding below targets a payload well under that.
    private const val MAX_ENCODED_IMAGE_BYTES = 500_000
    private const val MAX_IMAGE_DIMENSION_PX = 800

    /** Number of distinct user vouches required before a spot is auto-marked validated. */
    const val REQUIRED_VOUCHES = 3

    // save a study spot
    fun addSpot(spot: StudySpot) = spots.document(spot.id).set(spot.toFirestoreMap())

    fun uploadSpotImage(
        spotId: String,
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            onSuccess(bitmap.toDataUri())
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun Bitmap.toDataUri(): String {
        val scale = MAX_IMAGE_DIMENSION_PX.toFloat() / maxOf(width, height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            this
        }

        var quality = 80
        var bytes: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            bytes = baos.toByteArray()
            quality -= 15
        } while (bytes.size > MAX_ENCODED_IMAGE_BYTES && quality > 10)

        return "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    fun observeSpot(
        spotId: String,
        onSuccess: (StudySpot?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = spots.document(spotId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        onSuccess(snapshot?.takeIf { it.exists() }?.toStudySpot())
    }

    // read study spots back from Firestore
    fun observeSpots(
        onSuccess: (List<StudySpot>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = spots.addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        onSuccess(snapshot?.documents.orEmpty().mapNotNull { document ->
            document.toStudySpot()
        }.filter { it.status != SpotStatus.REMOVED })
    }

    fun getSpots(
        onSuccess: (List<StudySpot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        spots.get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { it.toStudySpot() }.filter { it.status != SpotStatus.REMOVED })
            }
            .addOnFailureListener { error -> onError(error) }
    }

    /** Spots created by the given user, including any they've soft-deleted (for self-service management). */
    fun spotsForUser(
        userId: String,
        onSuccess: (List<StudySpot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        spots.whereEqualTo("createdBy", userId).get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.documents.mapNotNull { it.toStudySpot() }) }
            .addOnFailureListener { onError(it) }
    }

    fun getSpotsByIds(
        ids: List<String>,
        onSuccess: (List<StudySpot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (ids.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        spots.whereIn(FieldPath.documentId(), ids.take(30)).get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.documents.mapNotNull { it.toStudySpot() }) }
            .addOnFailureListener { onError(it) }
    }

    /** Soft-deletes a spot by marking it REMOVED so it drops out of normal browsing while staying auditable. */
    fun deleteSpot(spotId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        spots.document(spotId).update("status", SpotStatus.REMOVED.name)
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e.message) }
    }

    fun vouchSpot(spotId: String, userId: String) = FirebaseFirestore.getInstance().runTransaction { transaction ->
        val spotRef = spots.document(spotId)
        val spot = transaction.get(spotRef)
        if (!spot.exists()) throw IllegalStateException("Study spot does not exist")

        if (spot.getString("createdBy") == userId) {
            throw IllegalStateException("You can't verify your own spot")
        }
        val vouchedBy = spot.get("vouchedBy") as? List<*> ?: emptyList<String>()
        if (vouchedBy.contains(userId)) {
            throw IllegalStateException("You already verified this spot")
        }

        val currentCount = (spot.getLong("requestCount") ?: 0L).toInt()
        val nextCount = currentCount + 1
        transaction.update(
            spotRef,
            mapOf(
                "requestCount" to nextCount,
                "isValidated" to (nextCount >= REQUIRED_VOUCHES),
                "vouchedBy" to FieldValue.arrayUnion(userId)
            )
        )
        null
    }

    // convert the Kotlin model to Firestore data
    private fun StudySpot.toFirestoreMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "description" to description,
        "address" to address,
        "latitude" to latitude,
        "longitude" to longitude,
        "amenities" to amenities,
        "hours" to hours,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "avgRating" to avgRating,
        "totalRatings" to totalRatings,
        "capacity" to capacity.name,
        "currentCheckIns" to currentCheckIns,
        "imageUrl" to imageUrl,
        "imageUrls" to imageUrls,
        "isValidated" to isValidated,
        "requestCount" to requestCount,
        "vouchedBy" to vouchedBy,
        "status" to status.name,
        "parentSpotId" to parentSpotId,
        "isHiddenGem" to isHiddenGem,
        "category" to category
    )

    // convert Firestore data into StudySpot()
    private fun com.google.firebase.firestore.DocumentSnapshot.toStudySpot(): StudySpot? {
        return try {
            StudySpot(
                id = id,
                name = getString("name").orEmpty(),
                description = getString("description").orEmpty(),
                address = getString("address").orEmpty(),
                latitude = getDouble("latitude") ?: 0.0,
                longitude = getDouble("longitude") ?: 0.0,
                amenities = get("amenities") as? List<String> ?: emptyList(),
                hours = getString("hours").orEmpty(),
                createdBy = getString("createdBy").orEmpty(),
                createdAt = getLong("createdAt") ?: 0L,
                avgRating = getDouble("avgRating") ?: 0.0,
                totalRatings = (getLong("totalRatings") ?: 0L).toInt(),
                capacity = getString("capacity")?.let { Capacity.valueOf(it) } ?: Capacity.SMALL,
                currentCheckIns = (getLong("currentCheckIns") ?: 0L).toInt(),
                imageUrl = getString("imageUrl"),
                imageUrls = get("imageUrls") as? List<String> ?: emptyList(),
                isValidated = getBoolean("isValidated") ?: false,
                requestCount = (getLong("requestCount") ?: 0L).toInt(),
                vouchedBy = get("vouchedBy") as? List<String> ?: emptyList(),
                status = getString("status")?.let { runCatching { SpotStatus.valueOf(it) }.getOrDefault(SpotStatus.ACTIVE) }
                    ?: SpotStatus.ACTIVE,
                parentSpotId = getString("parentSpotId"),
                isHiddenGem = getBoolean("isHiddenGem") ?: false,
                category = getString("category").orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }
}
