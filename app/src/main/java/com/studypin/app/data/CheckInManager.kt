package com.studypin.app.data

import com.studypin.app.model.StudySpot

/**
 * Manages the current check-in state for the user.
 */
object CheckInManager {
    var currentSpotId: String? = null
        private set

    fun checkIn(spotId: String) {
        currentSpotId = spotId
    }

    fun checkOut() {
        currentSpotId = null
    }

    fun isCheckedIn(spotId: String): Boolean {
        return currentSpotId == spotId
    }

    fun getCurrentSpot(): StudySpot? {
        return currentSpotId?.let { id ->
            MockData.studySpots.firstOrNull { it.id == id }
        }
    }
}
