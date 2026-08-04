package com.studypin.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

/** Firestore-backed active check-ins and occupancy history. */
object OccupancyRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val spots = firestore.collection("studySpots")

    fun observeCheckIn(
        spotId: String,
        userId: String,
        onSuccess: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = activeCheckIn(spotId, userId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
        } else {
            onSuccess(snapshot?.exists() == true)
        }
    }

    fun observeCurrentCheckIn(
        userId: String,
        onSuccess: (String?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = firestore.collectionGroup("activeCheckIns")
        .whereEqualTo("userId", userId)
        .limit(1)
        .addSnapshotListener { snapshot: QuerySnapshot?, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val spotId = snapshot?.documents?.firstOrNull()?.getString("spotId")
            onSuccess(spotId)
        }

    /** Checks the user into [spotId], auto-checking them out of any other spot first.
     *  Resolves to the id of the spot they were displaced from, or null if none. */
    fun checkIn(spotId: String, userId: String) = firestore.runTransaction { transaction ->
        val spotRef = spots.document(spotId)
        val checkInRef = activeCheckIn(spotId, userId)
        val historyRef = spotRef.collection("occupancyUpdates").document()
        val userCheckInRef = userCheckIn(userId)

        val spot = transaction.get(spotRef)
        val currentCheckIn = transaction.get(checkInRef)
        val userCheckInSnapshot = transaction.get(userCheckInRef)
        val previousSpotId = userCheckInSnapshot.getString("spotId")
            ?.takeIf { it != spotId }

        val previousSpotRef = previousSpotId?.let { spots.document(it) }
        val previousCheckInRef = previousSpotId?.let { activeCheckIn(it, userId) }
        val previousHistoryRef = previousSpotRef?.collection("occupancyUpdates")?.document()
        val previousSpot = previousSpotRef?.let { transaction.get(it) }

        if (!spot.exists()) throw IllegalStateException("Study spot does not exist")

        if (previousSpotId != null && previousSpot != null && previousSpot.exists() &&
            previousCheckInRef != null && previousHistoryRef != null
        ) {
            val previousCount = (previousSpot.getLong("currentCheckIns") ?: 0L).toInt()
            transaction.delete(previousCheckInRef)
            transaction.update(previousSpotRef, "currentCheckIns", (previousCount - 1).coerceAtLeast(0))
            transaction.set(previousHistoryRef, mapOf(
                "spotId" to previousSpotId,
                "userId" to userId,
                "status" to "out",
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }

        if (!currentCheckIn.exists()) {
            val currentCount = (spot.getLong("currentCheckIns") ?: 0L).toInt()
            transaction.set(checkInRef, mapOf(
                "spotId" to spotId,
                "userId" to userId,
                "checkedInAt" to FieldValue.serverTimestamp()
            ))
            transaction.update(spotRef, "currentCheckIns", currentCount + 1)
            transaction.set(historyRef, mapOf(
                "spotId" to spotId,
                "userId" to userId,
                "status" to "in",
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }

        transaction.set(userCheckInRef, mapOf(
            "spotId" to spotId,
            "checkedInAt" to FieldValue.serverTimestamp()
        ))

        previousSpotId
    }

    fun checkOut(spotId: String, userId: String) = firestore.runTransaction { transaction ->
        val spotRef = spots.document(spotId)
        val checkInRef = activeCheckIn(spotId, userId)
        val historyRef = spotRef.collection("occupancyUpdates").document()
        val userCheckInRef = userCheckIn(userId)
        val spot = transaction.get(spotRef)
        val currentCheckIn = transaction.get(checkInRef)
        val userCheckInSnapshot = transaction.get(userCheckInRef)

        if (!spot.exists()) throw IllegalStateException("Study spot does not exist")
        if (currentCheckIn.exists()) {
            val currentCount = (spot.getLong("currentCheckIns") ?: 0L).toInt()
            transaction.delete(checkInRef)
            transaction.update(spotRef, "currentCheckIns", (currentCount - 1).coerceAtLeast(0))
            transaction.set(historyRef, mapOf(
                "spotId" to spotId,
                "userId" to userId,
                "status" to "out",
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }
        if (userCheckInSnapshot.getString("spotId") == spotId) {
            transaction.delete(userCheckInRef)
        }
        null
    }

    private fun activeCheckIn(spotId: String, userId: String) =
        spots.document(spotId).collection("activeCheckIns").document(userId)

    private fun userCheckIn(userId: String) =
        firestore.collection("userCheckIns").document(userId)
}
