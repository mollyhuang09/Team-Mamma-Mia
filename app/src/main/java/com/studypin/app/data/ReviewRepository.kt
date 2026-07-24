package com.studypin.app.data

import com.google.firebase.database.FirebaseDatabase
import com.studypin.app.model.ReviewDisplayStats
import com.studypin.app.model.StudySpot
import com.studypin.app.model.StudySpotReview

/**
 * In-memory review repository used by the prototype.
 *
 * The same review shape is mirrored in the Firebase seed JSON so the
 * sample data can be imported to Realtime Database later.
 */
object ReviewRepository {

    private val database = FirebaseDatabase.getInstance("https://studypin-3f9fb-default-rtdb.firebaseio.com/").reference.child("reviews")

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

    fun hasUserReviewedSpot(userId: String, spotId: String): Boolean {
        return reviews.any {
            it.spotId == spotId && (it.reviewerId == userId || it.reviewerName == userId)
        }
    }

    fun addReview(review: StudySpotReview, onComplete: ((Boolean, String?) -> Unit)? = null) {
        reviews.add(0, review)
        // Push to Firebase Realtime Database
        database.push().setValue(review)
            .addOnSuccessListener {
                onComplete?.invoke(true, null)
            }
            .addOnFailureListener { e ->
                onComplete?.invoke(false, e.message)
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

        return ReviewDisplayStats(
            averageOverall = spotReviews.map { it.overallRating }.average(),
            reviewCount = spotReviews.size,
            amenityAverages = (listOf("noise", "seating") + spot.amenities).distinct().associateWith { amenity ->
                spotReviews.mapNotNull { it.amenityRatings[amenity] }.takeIf { it.isNotEmpty() }?.average()
                    ?: Double.NaN
            }.filterValues { !it.isNaN() }
        )
    }
}
