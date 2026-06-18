package com.studypin.app.ui.report

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.data.ReportEditRepository
import com.studypin.app.model.ReportEditSubmission

class ReportFlagFragment : Fragment() {
    private val defaultSpotId = "spot_mock_report"
    private val defaultSpotName = "Mock Study Spot"

    private lateinit var tvSpotContext: TextView
    private lateinit var categorySpinner: Spinner
    private lateinit var etSuggestedCorrection: EditText
    private lateinit var etDetails: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private lateinit var spotId: String
    private lateinit var spotName: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_flag, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSpotContext = view.findViewById(R.id.tvReportSpotContext)
        categorySpinner = view.findViewById(R.id.spinnerReportCategory)
        etSuggestedCorrection = view.findViewById(R.id.etSuggestedCorrection)
        etDetails = view.findViewById(R.id.etReportDetails)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)
        btnCancel = view.findViewById(R.id.btnCancelReport)

        setupSpotContext()
        setupCategorySpinner()

        btnSubmit.setOnClickListener { submitReport() }
        btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupSpotContext() {
        val argumentSpotId = arguments?.getString("spotId")
        val argumentSpotName = arguments?.getString("spotName")
        val matchingSpot = argumentSpotId?.let { id ->
            MockData.studySpots.firstOrNull { it.id == id }
        }

        spotId = argumentSpotId ?: matchingSpot?.id ?: defaultSpotId
        spotName = argumentSpotName ?: matchingSpot?.name ?: defaultSpotName

        tvSpotContext.text = "For: $spotName ($spotId)"
    }

    private fun setupCategorySpinner() {
        val categories = listOf(
            "Select a category *",
            "Incorrect hours",
            "Incorrect address/location",
            "Incorrect amenities",
            "Inappropriate or misleading information",
            "Duplicate spot",
            "Other"
        )

        categorySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categories
        )
    }

    private fun submitReport() {
        if (categorySpinner.selectedItemPosition == 0) {
            Toast.makeText(requireContext(), "Choose a report category", Toast.LENGTH_SHORT).show()
            return
        }

        val details = etDetails.text.toString().trim()
        if (details.isEmpty()) {
            Toast.makeText(requireContext(), "Additional details are required", Toast.LENGTH_SHORT).show()
            return
        }

        val submission = ReportEditSubmission(
            spotId = spotId,
            spotName = spotName,
            category = categorySpinner.selectedItem.toString(),
            suggestedCorrection = etSuggestedCorrection.text.toString().trim(),
            details = details,
            timestamp = System.currentTimeMillis(),
            status = "pending"
        )

        ReportEditRepository.submit(submission)
        showSuccessConfirmation()
    }

    private fun showSuccessConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Report submitted")
            .setMessage("Thanks for helping keep StudyPin accurate. Your report is pending review.")
            .setPositiveButton("OK") { _, _ ->
                clearForm()
                findNavController().popBackStack()
            }
            .show()
    }

    private fun clearForm() {
        categorySpinner.setSelection(0)
        etSuggestedCorrection.text.clear()
        etDetails.text.clear()
    }
}
