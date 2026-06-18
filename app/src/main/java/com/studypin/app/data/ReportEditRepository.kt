package com.studypin.app.data

import com.studypin.app.model.ReportEditSubmission

object ReportEditRepository {
    private val submissions = mutableListOf<ReportEditSubmission>()

    fun submit(submission: ReportEditSubmission) {
        submissions.add(submission)
    }

    fun allSubmissions(): List<ReportEditSubmission> = submissions.toList()
}
