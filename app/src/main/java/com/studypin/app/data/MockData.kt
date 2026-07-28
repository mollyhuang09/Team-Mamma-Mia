package com.studypin.app.data

import com.studypin.app.model.Capacity
import com.studypin.app.model.StudySpot
import com.studypin.app.model.SpotStatus

/**
 * Hardcoded mock data for UI development before Firestore wiring is complete.
 * Mix of three categories to demo the "hidden gems" angle of StudyPin:
 *   - Well-known campus spots (top-level, large capacity)
 *   - Hidden campus corners (some top-level, some nested as hidden gems)
 *   - Off-campus finds (top-level)
 *
 * spot_004 and spot_005 are hidden gems nested inside spot_001 (Dana Porter)
 * and spot_003 (Conrad Grebel) respectively, to demo the parent/child UI.
 */
object MockData {

    val studySpots: List<StudySpot> = listOf(

        // ---------- Well-known campus spots (top-level) ----------

        StudySpot(
            id = "spot_001",
            name = "Dana Porter Library",
            description = "The classic. Main campus library with quiet zones on the upper floors — everyone's first stop.",
            address = "200 University Ave W, Waterloo, ON",
            latitude = 43.4698,
            longitude = -80.5426,
            amenities = listOf("wifi", "outlets", "washroom", "printing"),
            hours = "8:00 AM - 11:00 PM",
            createdBy = "device_demo_01",
            avgRating = 4.5,
            totalRatings = 142,
            capacity = Capacity.LARGE,
            currentCheckIns = 58,
            parentSpotId = null,
            isHiddenGem = false
        ),

        StudySpot(
            id = "spot_002",
            name = "SLC Study Lounge",
            description = "Open lounge in the Student Life Centre, the go-to spot between classes for most students.",
            address = "200 University Ave W, Waterloo, ON",
            latitude = 43.4723,
            longitude = -80.5449,
            amenities = listOf("wifi", "outlets"),
            hours = "7:00 AM - 12:00 AM",
            createdBy = "device_demo_02",
            avgRating = 4.0,
            totalRatings = 98,
            capacity = Capacity.MEDIUM,
            currentCheckIns = 44,
            status = SpotStatus.UNDER_REVIEW,
            lastVerifiedLabel = "Mar 12, 2025",
            parentSpotId = null,
            isHiddenGem = false
        ),

        // ---------- Hidden campus corners (top-level) ----------

        StudySpot(
            id = "spot_003",
            name = "Conrad Grebel Reading Room",
            description = "Tucked away in a college most students forget exists. Quiet, sunlit, almost never full.",
            address = "140 Westmount Rd N, Waterloo, ON",
            latitude = 43.4735,
            longitude = -80.5462,
            amenities = listOf("wifi", "outlets", "washroom"),
            hours = "9:00 AM - 10:00 PM",
            createdBy = "device_demo_03",
            avgRating = 4.9,
            totalRatings = 14,
            capacity = Capacity.SMALL,
            currentCheckIns = 2,
            parentSpotId = null,
            isHiddenGem = false
        ),

        // ---------- Hidden gems (nested inside the spots above) ----------

        StudySpot(
            id = "spot_004",
            name = "3rd Floor East Window Desk",
            description = "A single desk by the east windows in Dana Porter, easy to miss but rarely taken.",
            address = "200 University Ave W, Waterloo, ON",
            latitude = 43.4698,
            longitude = -80.5426,
            amenities = listOf("wifi", "outlets"),
            hours = "8:00 AM - 11:00 PM",
            createdBy = "device_demo_04",
            avgRating = 4.8,
            totalRatings = 5,
            capacity = Capacity.TINY,
            currentCheckIns = 1,
            parentSpotId = "spot_001",
            isHiddenGem = true
        ),

        StudySpot(
            id = "spot_005",
            name = "Grebel Courtyard Bench",
            description = "A single outdoor bench in the inner courtyard, perfect on a sunny day.",
            address = "140 Westmount Rd N, Waterloo, ON",
            latitude = 43.4735,
            longitude = -80.5462,
            amenities = listOf(),
            hours = "Dawn - dusk",
            createdBy = "device_demo_01",
            avgRating = 4.6,
            totalRatings = 3,
            capacity = Capacity.TINY,
            currentCheckIns = 0,
            parentSpotId = "spot_003",
            isHiddenGem = true
        ),

        // ---------- Off-campus finds (top-level) ----------

        StudySpot(
            id = "spot_006",
            name = "Vincenzo's Loft Seating",
            description = "Upstairs seating above the grocery store deli — quiet, oddly cozy, and rarely discovered by students.",
            address = "91 King St S, Waterloo, ON",
            latitude = 43.4642,
            longitude = -80.5204,
            amenities = listOf("wifi"),
            hours = "9:00 AM - 8:00 PM",
            createdBy = "device_demo_03",
            avgRating = 4.4,
            totalRatings = 6,
            capacity = Capacity.SMALL,
            currentCheckIns = 3,
            parentSpotId = null,
            isHiddenGem = false
        ),

        StudySpot(
            id = "spot_007",
            name = "Waterloo Park Pavilion",
            description = "Covered outdoor pavilion by the duck pond. Great for studying when the weather's good.",
            address = "Waterloo Park, Waterloo, ON",
            latitude = 43.4661,
            longitude = -80.5180,
            amenities = listOf(),
            hours = "Dawn - dusk",
            createdBy = "device_demo_04",
            avgRating = 4.2,
            totalRatings = 5,
            capacity = Capacity.SMALL,
            currentCheckIns = 0,
            parentSpotId = null,
            isHiddenGem = false
        ),

        StudySpot(
            id = "spot_008",
            name = "Bench Coffee Co. Back Room",
            description = "A quiet back room past the counter that most regulars don't even know is there.",
            address = "200 Frederick St, Kitchener, ON",
            latitude = 43.4516,
            longitude = -80.4925,
            amenities = listOf("wifi", "outlets"),
            hours = "7:00 AM - 6:00 PM",
            createdBy = "device_demo_01",
            avgRating = 4.8,
            totalRatings = 13,
            capacity = Capacity.SMALL,
            currentCheckIns = 8,
            status = SpotStatus.TEMPORARILY_CLOSED,
            lastVerifiedLabel = "Mar 12, 2025",
            parentSpotId = null,
            isHiddenGem = false
        ),

        StudySpot(
            id = "spot_009",
            name = "Kitchener Public Library - Idea Exchange",
            description = "A 10-minute drive away, but almost always quiet with great natural light and few students know about it.",
            address = "85 Queen St N, Kitchener, ON",
            latitude = 43.4506,
            longitude = -80.4881,
            amenities = listOf("wifi", "outlets", "washroom", "printing"),
            hours = "9:00 AM - 9:00 PM",
            createdBy = "device_demo_02",
            avgRating = 4.6,
            totalRatings = 21,
            capacity = Capacity.MEDIUM,
            currentCheckIns = 6,
            parentSpotId = null,
            isHiddenGem = false
        )
    )

    /** Returns only top-level spots — used for map pins and the main list. */
    fun topLevelSpots(): List<StudySpot> = studySpots.filter { it.parentSpotId == null }

    /** Returns the hidden gems nested inside a given parent spot's detail page. */
    fun hiddenGemsFor(parentId: String): List<StudySpot> =
        studySpots.filter { it.parentSpotId == parentId }

    fun hiddenGemCountFor(parentId: String): Int =
        studySpots.count { it.parentSpotId == parentId }
    /**
     * Sample ratings for spot_001 (Dana Porter Library), for the detail/rating screens.
     */
    val sampleRatings = listOf(
        mapOf("spotId" to "spot_001", "noise" to 3, "wifi" to 4, "seating" to 3, "outlets" to 4, "overall" to 4),
        mapOf("spotId" to "spot_001", "noise" to 4, "wifi" to 5, "seating" to 4, "outlets" to 4, "overall" to 4),
        mapOf("spotId" to "spot_001", "noise" to 3, "wifi" to 4, "seating" to 5, "outlets" to 5, "overall" to 5)
    )
}
