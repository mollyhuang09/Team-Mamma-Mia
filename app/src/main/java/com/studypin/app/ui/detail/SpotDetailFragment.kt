package com.studypin.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R
import com.studypin.app.data.MockData

class SpotDetailFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_spot_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
}
