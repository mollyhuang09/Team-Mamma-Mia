package com.studypin.app.model

/**
 * User review data for a study spot.
 *
 * The model is intentionally Firebase-friendly: it only uses simple
 * primitives plus a map for amenity scores so it can be stored in
 * Realtime Database / Firestore without custom adapters.
 */
data class StudySpotReview(
    val id: String = "",
    val spotId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "Anonymous",
    val overallRating: Int = 0,
    val amenityRatings: Map<String, Int> = emptyMap(),
    val descriptors: List<String> = emptyList(),
    val reviewText: String = "",
    val visitTimeOfDay: String = "",
    val crowdLevel: String = "",
    val mediaCount: Int = 0,
    val helpfulCount: Int = 0,
    val submittedAtLabel: String = ""
)

/**
 * Aggregated review data for a single spot.
 */
data class ReviewDisplayStats(
    val averageOverall: Double = 0.0,
    val reviewCount: Int = 0,
    val amenityAverages: Map<String, Double> = emptyMap()
)
