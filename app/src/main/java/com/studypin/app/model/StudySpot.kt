package com.studypin.app.model

/**
 * Capacity buckets used instead of an exact seat count, since most
 * spots (especially hidden gems) won't have a precise number and
 * exact counts would be misleading anyway once a few seats are taken.
 */
enum class Capacity(val label: String, val approxSeats: Int) {
    TINY("Tiny (1-2 seats)", 2),
    SMALL("Small (~10 seats)", 10),
    MEDIUM("Medium (~50 seats)", 50),
    LARGE("Large (100+ seats)", 120)
}

/** Lifecycle/moderation state of a study spot, independent of occupancy. */
enum class SpotStatus {
    ACTIVE,
    UNDER_REVIEW,
    TEMPORARILY_CLOSED,
    REMOVED
}

fun SpotStatus.isInactive(): Boolean = when (this) {
    SpotStatus.UNDER_REVIEW,
    SpotStatus.TEMPORARILY_CLOSED -> true
    SpotStatus.ACTIVE,
    SpotStatus.REMOVED -> false
}

/**
 * Represents a single study spot — either a top-level location
 * (e.g. "DC Library") or a hidden gem nested inside one
 * (e.g. "3rd floor quiet corner", parentSpotId = DC Library's id).
 *
 * Occupancy is tracked via active check-ins rather than a manual
 * status button, since a single "Available/Busy" label doesn't mean
 * the same thing for a 1-seat bench as it does for a 200-seat library.
 * The displayed label is computed from currentCheckIns / capacity.
 */
data class StudySpot(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val amenities: List<String> = emptyList(),
    val hours: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val avgRating: Double = 0.0,
    val totalRatings: Int = 0,

    // --- Occupancy via check-ins ---
    val capacity: Capacity = Capacity.SMALL,
    val currentCheckIns: Int = 0,

    // --- Validation & Photos ---
    // imageUrl is kept for compatibility with existing spot data.
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val isValidated: Boolean = false,
    val requestCount: Int = 0,

    // --- Lifecycle/moderation status ---
    val status: SpotStatus = SpotStatus.ACTIVE,

    // Display label for the most recent verification. This can be populated
    // from the database once verification timestamps are stored there.
    val lastVerifiedLabel: String = "",

    // --- Hidden gem nesting ---
    val parentSpotId: String? = null,
    val isHiddenGem: Boolean = false
) {
    /**
     * Computes a human-readable occupancy label from the ratio of
     * active check-ins to capacity. Works the same way regardless of
     * whether this is a top-level spot or a hidden gem, since each
     * spot has its own independent capacity and check-in count.
     */
    fun occupancyLabel(): String {
        if (currentCheckIns <= 0) return "Empty"
        val ratio = currentCheckIns.toDouble() / capacity.approxSeats
        return when {
            ratio < 0.5 -> "Available"
            ratio < 0.9 -> "Filling up"
            else -> "Full"
        }
    }
}
