package com.studypin.app.data

import com.google.firebase.firestore.FirebaseFirestore

/** Persistence for the user profile document keyed by Firebase Auth UID. */
object UserProfileRepository {
    private val users = FirebaseFirestore.getInstance().collection("users")

    fun saveProfile(
        uid: String,
        displayName: String,
        email: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        val data = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "email" to email,
            "updatedAt" to System.currentTimeMillis()
        )
        users.document(uid).set(data)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { error -> onComplete(error) }
    }

    fun loadProfile(
        uid: String,
        onSuccess: (displayName: String?, email: String?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        users.document(uid).get()
            .addOnSuccessListener { document ->
                onSuccess(document.getString("displayName"), document.getString("email"))
            }
            .addOnFailureListener { error -> onError(error) }
    }
}
