package com.studypin.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot

/** Firestore access for the shared studySpots collection. */
object StudySpotRepository {
    private val spots = FirebaseFirestore.getInstance().collection("studySpots")

    // save a study spot
    fun addSpot(spot: StudySpot) = spots.document(spot.id).set(spot.toFirestoreMap())

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
        })
    }

    fun getSpots(
        onSuccess: (List<StudySpot>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        spots.get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { it.toStudySpot() })
            }
            .addOnFailureListener { error -> onError(error) }
    }

    fun vouchSpot(spotId: String, userId: String) = FirebaseFirestore.getInstance().runTransaction { transaction ->
        val spotRef = spots.document(spotId)
        val spot = transaction.get(spotRef)
        if (!spot.exists()) throw IllegalStateException("Study spot does not exist")

        val currentCount = (spot.getLong("requestCount") ?: 0L).toInt()
        val nextCount = currentCount + 1
        transaction.update(
            spotRef,
            mapOf(
                "requestCount" to nextCount,
                "isValidated" to (nextCount >= 2),
                "lastVouchedBy" to userId
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
        "isValidated" to isValidated,
        "requestCount" to requestCount,
        "parentSpotId" to parentSpotId,
        "isHiddenGem" to isHiddenGem
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
                isValidated = getBoolean("isValidated") ?: false,
                requestCount = (getLong("requestCount") ?: 0L).toInt(),
                parentSpotId = getString("parentSpotId"),
                isHiddenGem = getBoolean("isHiddenGem") ?: false
            )
        } catch (_: Exception) {
            null
        }
    }
}
