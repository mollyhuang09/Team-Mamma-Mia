package com.studypin.app.data

import com.studypin.app.model.ReportEditSubmission

object ReportEditRepository {
    private val firestoreReports = com.google.firebase.firestore.FirebaseFirestore
        .getInstance()
        .collection("reports")
    private val submissions = mutableListOf<ReportEditSubmission>()

    fun submit(submission: ReportEditSubmission, onComplete: (Exception?) -> Unit = {}) {
        submissions.add(submission)
        firestoreReports.add(submission)
            .addOnSuccessListener { onComplete(null) }
            .addOnFailureListener { error -> onComplete(error) }
    }

    fun allSubmissions(): List<ReportEditSubmission> = submissions.toList()
}
