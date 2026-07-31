package com.studypin.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.model.ReviewDisplayStats
import com.studypin.app.model.StudySpot
import com.studypin.app.model.StudySpotReview

/** Firestore-backed ratings repository with a small in-memory UI cache. */
object ReviewRepository {

    private val helpfulVotesByCurrentUser = mutableSetOf<String>()
    private val firestore = FirebaseFirestore.getInstance()
    private val spots = firestore.collection("studySpots")

    private val reviews = mutableListOf(
        StudySpotReview(
            id = "review_001",
            spotId = "spot_001",
            reviewerName = "Alyssa",
            overallRating = 5,
            amenityRatings = mapOf("wifi" to 5, "outlets" to 4, "washroom" to 4, "printing" to 4),
            reviewText = "Quiet in the morning and the upper floors still feel peaceful even during midterms.",
            visitTimeOfDay = "Morning",
            crowdLevel = "Quiet",
            mediaCount = 2,
            helpfulCount = 12,
            submittedAtLabel = "Today"
        ),
        StudySpotReview(
            id = "review_002",
            spotId = "spot_001",
            reviewerName = "Ben",
            overallRating = 4,
            amenityRatings = mapOf("wifi" to 4, "outlets" to 5, "washroom" to 4, "printing" to 5),
            reviewText = "Reliable Wi‑Fi and lots of outlets near the reading areas. The main floor gets busy after lunch.",
            visitTimeOfDay = "Afternoon",
            crowdLevel = "Busy",
            mediaCount = 1,
            helpfulCount = 12,
            submittedAtLabel = "Yesterday"
        ),
        StudySpotReview(
            id = "review_003",
            spotId = "spot_001",
            reviewerName = "Nora",
            overallRating = 4,
            amenityRatings = mapOf("wifi" to 4, "outlets" to 4, "washroom" to 5, "printing" to 4),
            reviewText = "A good fallback spot when everything else is packed. Washrooms are easy to find and the place is well lit.",
            visitTimeOfDay = "Evening",
            crowdLevel = "Moderate",
            mediaCount = 0,
            helpfulCount = 8,
            submittedAtLabel = "3 days ago"
        ),
        StudySpotReview(
            id = "review_004",
            spotId = "spot_002",
            reviewerName = "Chris",
            overallRating = 4,
            amenityRatings = mapOf("wifi" to 4, "outlets" to 4),
            reviewText = "Great between classes if you only need a quick session. Better for short stays than deep focus.",
            visitTimeOfDay = "Afternoon",
            crowdLevel = "Busy",
            mediaCount = 0,
            submittedAtLabel = "Today"
        ),
        StudySpotReview(
            id = "review_005",
            spotId = "spot_003",
            reviewerName = "Mina",
            overallRating = 5,
            amenityRatings = mapOf("wifi" to 5, "outlets" to 4, "washroom" to 4),
            reviewText = "Very calm and bright. I usually come here when I want a quieter change of scenery.",
            visitTimeOfDay = "Morning",
            crowdLevel = "Quiet",
            mediaCount = 1,
            submittedAtLabel = "Yesterday"
        ),
        StudySpotReview(
            id = "review_006",
            spotId = "spot_006",
            reviewerName = "Jordan",
            overallRating = 4,
            amenityRatings = mapOf("wifi" to 4, "outlets" to 3),
            reviewText = "Cozy upstairs area with enough table space for solo work. Best before the dinner rush.",
            visitTimeOfDay = "Evening",
            crowdLevel = "Moderate",
            mediaCount = 0,
            submittedAtLabel = "Last week"
        ),
        StudySpotReview(
            id = "review_007",
            spotId = "spot_004",
            reviewerName = "Sam",
            overallRating = 5,
            amenityRatings = mapOf("wifi" to 5, "outlets" to 5),
            reviewText = "Perfect for deep focus. Literally one desk, but it's the best desk in the building.",
            visitTimeOfDay = "Morning",
            crowdLevel = "Quiet",
            mediaCount = 1,
            submittedAtLabel = "2 days ago"
        ),
        StudySpotReview(
            id = "review_008",
            spotId = "spot_009",
            reviewerName = "Alex",
            overallRating = 5,
            amenityRatings = mapOf("wifi" to 5, "outlets" to 4, "washroom" to 5, "printing" to 5),
            reviewText = "KPL is a bit of a trek but so worth it. The Idea Exchange area is super modern.",
            visitTimeOfDay = "Afternoon",
            crowdLevel = "Moderate",
            mediaCount = 3,
            submittedAtLabel = "1 week ago"
        )
    )

    fun reviewsForSpot(spotId: String): List<StudySpotReview> =
        reviews.filter { it.spotId == spotId }

    fun helpfulCountFor(reviewId: String): Int =
        reviews.firstOrNull { it.id == reviewId }?.helpfulCount ?: 0

    fun isHelpfulByCurrentUser(reviewId: String): Boolean =
        helpfulVotesByCurrentUser.contains(reviewId)

    /** Toggles the current user's local helpful vote and returns the new selected state. */
    fun toggleHelpful(reviewId: String): Boolean {
        val index = reviews.indexOfFirst { it.id == reviewId }
        if (index == -1) return false

        val review = reviews[index]
        return if (helpfulVotesByCurrentUser.add(reviewId)) {
            reviews[index] = review.copy(helpfulCount = review.helpfulCount + 1)
            true
        } else {
            helpfulVotesByCurrentUser.remove(reviewId)
            reviews[index] = review.copy(helpfulCount = (review.helpfulCount - 1).coerceAtLeast(0))
            false
        }
    }

    fun hasUserReviewedSpot(userId: String, spotId: String): Boolean {
        return reviews.any {
            it.spotId == spotId && (it.reviewerId == userId || it.reviewerName == userId)
        }
    }

    fun observeReviews(
        spotId: String,
        onSuccess: (List<StudySpotReview>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = spots.document(spotId).collection("ratings")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val loaded = snapshot?.documents.orEmpty().mapNotNull { document ->
                document.toReview()
            }
            reviews.removeAll { it.spotId == spotId }
            reviews.addAll(loaded)
            onSuccess(loaded)
        }

    /**
     * Cross-spot query: reviews live in per-spot "ratings" subcollections, so this needs a
     * Firestore collection group query, which requires a matching index to be created in the
     * Firebase console (collection group "ratings", field reviewerId) before it will succeed.
     */
    fun reviewsByUser(
        userId: String,
        onSuccess: (List<StudySpotReview>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collectionGroup("ratings").whereEqualTo("reviewerId", userId).get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.documents.mapNotNull { it.toReview() }) }
            .addOnFailureListener { onError(it) }
    }

    fun deleteReview(spotId: String, reviewId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val spotRef = spots.document(spotId)
        val reviewRef = spotRef.collection("ratings").document(reviewId)
        firestore.runTransaction { transaction ->
            val spot = transaction.get(spotRef)
            val review = transaction.get(reviewRef)
            if (!spot.exists() || !review.exists()) throw IllegalStateException("Review or spot does not exist")

            val oldCount = (spot.getLong("totalRatings") ?: 0L).toInt()
            val removedRating = (review.getLong("overallRating") ?: 0L).toInt()
            val newCount = (oldCount - 1).coerceAtLeast(0)
            val newAverage = if (newCount == 0) {
                0.0
            } else {
                (((spot.getDouble("avgRating") ?: 0.0) * oldCount) - removedRating) / newCount
            }

            transaction.delete(reviewRef)
            transaction.update(spotRef, mapOf("totalRatings" to newCount, "avgRating" to newAverage))
            null
        }.addOnSuccessListener {
            reviews.removeAll { it.id == reviewId }
            onComplete?.invoke(true, null)
        }.addOnFailureListener { e ->
            onComplete?.invoke(false, e.message)
        }
    }

    fun addReview(review: StudySpotReview, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val spotRef = spots.document(review.spotId)
        val reviewRef = spotRef.collection("ratings").document(review.id)

        firestore.runTransaction { transaction ->
            val spot = transaction.get(spotRef)
            val existingReview = transaction.get(reviewRef)
            if (!spot.exists()) throw IllegalStateException("Study spot does not exist")
            if (!existingReview.exists()) {
                val oldCount = (spot.getLong("totalRatings") ?: 0L).toInt()
                val oldAverage = spot.getDouble("avgRating") ?: 0.0
                val newCount = oldCount + 1
                val newAverage = ((oldAverage * oldCount) + review.overallRating) / newCount
                transaction.set(reviewRef, review.toFirestoreMap())
                transaction.update(spotRef, mapOf(
                    "totalRatings" to newCount,
                    "avgRating" to newAverage
                ))
            }
            null
        }.addOnSuccessListener {
            reviews.removeAll { it.id == review.id }
            reviews.add(0, review)
            onComplete?.invoke(true, null)
        }.addOnFailureListener { e ->
            onComplete?.invoke(false, e.message)
        }
    }

    private fun StudySpotReview.toFirestoreMap(): Map<String, Any?> = mapOf(
        "spotId" to spotId,
        "reviewerId" to reviewerId,
        "reviewerName" to reviewerName,
        "overallRating" to overallRating,
        "amenityRatings" to amenityRatings,
        "reviewText" to reviewText,
        "visitTimeOfDay" to visitTimeOfDay,
        "crowdLevel" to crowdLevel,
        "mediaCount" to mediaCount,
        "submittedAtLabel" to submittedAtLabel,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toReview(): StudySpotReview? {
        return try {
            StudySpotReview(
                id = id,
                spotId = getString("spotId").orEmpty(),
                reviewerId = getString("reviewerId").orEmpty(),
                reviewerName = getString("reviewerName") ?: "Anonymous",
                overallRating = (getLong("overallRating") ?: 0L).toInt(),
                amenityRatings = (get("amenityRatings") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
                    val amenity = key as? String ?: return@mapNotNull null
                    val rating = (value as? Number)?.toInt() ?: return@mapNotNull null
                    amenity to rating
                }.toMap(),
                reviewText = getString("reviewText").orEmpty(),
                visitTimeOfDay = getString("visitTimeOfDay").orEmpty(),
                crowdLevel = getString("crowdLevel").orEmpty(),
                mediaCount = (getLong("mediaCount") ?: 0L).toInt(),
                submittedAtLabel = getString("submittedAtLabel").orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun displayStatsForSpot(spot: StudySpot): ReviewDisplayStats {
        val spotReviews = reviewsForSpot(spot.id)
        if (spotReviews.isEmpty()) {
            return ReviewDisplayStats(
                averageOverall = spot.avgRating,
                reviewCount = spot.totalRatings,
                amenityAverages = emptyMap()
            )
        }

        val rateableAmenities = (listOf("noise", "seating") + spot.amenities)
            .distinct()
            .filterNot { it.equals("printing", ignoreCase = true) }

        return ReviewDisplayStats(
            averageOverall = spotReviews.map { it.overallRating }.average(),
            reviewCount = spotReviews.size,
            amenityAverages = rateableAmenities.associateWith { amenity ->
                spotReviews.mapNotNull { it.amenityRatings[amenity] }.takeIf { it.isNotEmpty() }?.average()
                    ?: Double.NaN
            }.filterValues { !it.isNaN() }
        )
    }
}
