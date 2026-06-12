package com.studypin.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R

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
            findNavController().navigate(R.id.action_spotDetail_to_reportFlag)
        }
    }
}