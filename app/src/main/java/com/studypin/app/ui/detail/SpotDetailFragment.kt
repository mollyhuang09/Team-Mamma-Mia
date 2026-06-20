package com.studypin.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.StudySpot
import java.util.Locale

class SpotDetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_spot_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spotId = arguments?.getString("spotId")
        val spot = MockData.studySpots.firstOrNull { it.id == spotId }

        if (spot == null) {
            showMissingSpot(view)
        } else {
            bindSpot(view, spot)
        }

        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<Button>(R.id.btnEdit).setOnClickListener {
            findNavController().navigate(R.id.action_spotDetail_to_editSpot)
        }
        view.findViewById<Button>(R.id.btnReport).setOnClickListener {
            val spotId = arguments?.getString("spotId")
            val spotName = arguments?.getString("spotName")
                ?: MockData.studySpots.firstOrNull { it.id == spotId }?.name

            val bundle = Bundle().apply {
                spotId?.let { putString("spotId", it) }
                spotName?.let { putString("spotName", it) }
            }

            findNavController().navigate(R.id.action_spotDetail_to_reportFlag, bundle)
        }
    }

    private fun bindSpot(view: View, spot: StudySpot) {
        view.findViewById<TextView>(R.id.tvSpotName).text = spot.name
        view.findViewById<TextView>(R.id.tvHiddenGem).visibility =
            if (spot.isHiddenGem) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvAvailability).text = spot.occupancyLabel()
        view.findViewById<TextView>(R.id.tvOccupancyDetail).text =
            "${spot.currentCheckIns} checked in · ${spot.capacity.label}"
        view.findViewById<TextView>(R.id.tvRatingDetail).text = String.format(
            Locale.CANADA, "★ %.1f / 5 (%d rating%s)", spot.avgRating, spot.totalRatings,
            if (spot.totalRatings == 1) "" else "s"
        )
        view.findViewById<TextView>(R.id.tvDescription).text = spot.description
        view.findViewById<TextView>(R.id.tvLocation).text = spot.address
        view.findViewById<TextView>(R.id.tvHours).text = spot.hours
        view.findViewById<TextView>(R.id.tvAmenitiesDetail).text = if (spot.amenities.isEmpty()) {
            "No amenities listed"
        } else {
            spot.amenities.joinToString(" · ") { it.replaceFirstChar(Char::titlecase) }
        }
    }

    private fun showMissingSpot(view: View) {
        view.findViewById<TextView>(R.id.tvSpotName).text = "Study spot unavailable"
        view.findViewById<TextView>(R.id.tvDescription).text =
            "This study spot could not be found. Return to the list and choose another spot."
        listOf(R.id.tvAvailability, R.id.tvOccupancyDetail, R.id.tvRatingDetail,
            R.id.tvLocation, R.id.tvHours, R.id.tvAmenitiesDetail, R.id.btnEdit, R.id.btnReport)
            .forEach { view.findViewById<View>(it).visibility = View.GONE }
    }
}
