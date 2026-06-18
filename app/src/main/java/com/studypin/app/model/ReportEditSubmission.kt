package com.studypin.app.model

data class ReportEditSubmission(
    val spotId: String,
    val spotName: String,
    val category: String,
    val suggestedCorrection: String,
    val details: String,
    val timestamp: Long,
    val status: String = "pending"
)
