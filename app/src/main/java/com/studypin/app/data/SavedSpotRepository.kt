package com.studypin.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Firestore access for a user's saved/bookmarked spots, stored as users/{uid}/savedSpots/{spotId}. */
object SavedSpotRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private fun savedSpotsRef(userId: String) =
        firestore.collection("users").document(userId).collection("savedSpots")

    fun saveSpot(userId: String, spotId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        savedSpotsRef(userId).document(spotId).set(mapOf("savedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e.message) }
    }

    fun unsaveSpot(userId: String, spotId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        savedSpotsRef(userId).document(spotId).delete()
            .addOnSuccessListener { onComplete?.invoke(true, null) }
            .addOnFailureListener { e -> onComplete?.invoke(false, e.message) }
    }

    fun isSpotSaved(userId: String, spotId: String, onResult: (Boolean) -> Unit) {
        savedSpotsRef(userId).document(spotId).get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    /** Returns saved spot ids only; resolve to full StudySpots via StudySpotRepository.getSpotsByIds. */
    fun observeSavedSpotIds(
        userId: String,
        onSuccess: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = savedSpotsRef(userId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        onSuccess(snapshot?.documents.orEmpty().map { it.id })
    }
}
