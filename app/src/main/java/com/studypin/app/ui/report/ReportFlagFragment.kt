package com.studypin.app.ui.report

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.data.ReportEditRepository
import com.studypin.app.model.ReportEditSubmission
import com.google.android.material.textfield.TextInputLayout

class ReportFlagFragment : Fragment() {
    private val defaultSpotId = "spot_mock_report"
    private val defaultSpotName = "Mock Study Spot"

    private lateinit var tvSpotContext: TextView
    private lateinit var tvPlaceContext: TextView
    private lateinit var categoryInput: AutoCompleteTextView
    private lateinit var categoryLayout: TextInputLayout
    private lateinit var detailsLayout: TextInputLayout
    private lateinit var etSuggestedCorrection: EditText
    private lateinit var etDetails: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private lateinit var spotId: String
    private lateinit var spotName: String
    private var spotsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_report_flag, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSpotContext = view.findViewById(R.id.tvReportSpotContext)
        tvPlaceContext = view.findViewById(R.id.tvReportPlaceContext)
        categoryInput = view.findViewById(R.id.actvReportCategory)
        categoryLayout = view.findViewById(R.id.tilReportCategory)
        detailsLayout = view.findViewById(R.id.tilReportDetails)
        etSuggestedCorrection = view.findViewById(R.id.etSuggestedCorrection)
        etDetails = view.findViewById(R.id.etReportDetails)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)
        btnCancel = view.findViewById(R.id.btnCancelReport)

        setupSpotContext()
        setupCategoryDropdown()

        btnSubmit.setOnClickListener { submitReport() }
        btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupSpotContext() {
        val argumentSpotId = arguments?.getString("spotId")
        val argumentSpotName = arguments?.getString("spotName")
        spotId = argumentSpotId ?: defaultSpotId
        spotName = argumentSpotName ?: defaultSpotName

        tvSpotContext.text = spotName
        tvPlaceContext.visibility = View.GONE

        if (argumentSpotId == null) return
        spotsListener = StudySpotRepository.observeSpots(
            onSuccess = { spots ->
                if (!isAdded) return@observeSpots
                val matchingSpot = spots.firstOrNull { it.id == spotId } ?: return@observeSpots
                if (argumentSpotName == null) {
                    spotName = matchingSpot.name
                    tvSpotContext.text = spotName
                }
                val placeName = matchingSpot.parentSpotId?.let { parentId ->
                    spots.firstOrNull { it.id == parentId }?.name
                }
                if (placeName.isNullOrBlank()) {
                    tvPlaceContext.visibility = View.GONE
                } else {
                    tvPlaceContext.text = placeName
                    tvPlaceContext.visibility = View.VISIBLE
                }
            },
            onError = { /* Navigation arguments are enough to submit the report. */ }
        )
    }

    private fun setupCategoryDropdown() {
        val categories = listOf(
            "Incorrect hours",
            "Incorrect address/location",
            "Incorrect amenities",
            "Inappropriate or misleading information",
            "Duplicate spot",
            "Other"
        )

        categoryInput.setAdapter(ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        ))
        categoryInput.setOnClickListener { categoryInput.showDropDown() }
    }

    private fun submitReport() {
        categoryLayout.error = null
        detailsLayout.error = null

        val category = categoryInput.text.toString().trim()
        if (category.isEmpty()) {
            categoryLayout.error = "Choose a problem category"
            Toast.makeText(requireContext(), "Choose a report category", Toast.LENGTH_SHORT).show()
            return
        }

        val details = etDetails.text.toString().trim()
        if (details.isEmpty()) {
            detailsLayout.error = "Add a few details"
            Toast.makeText(requireContext(), "Additional details are required", Toast.LENGTH_SHORT).show()
            return
        }

        val submission = ReportEditSubmission(
            spotId = spotId,
            spotName = spotName,
            category = category,
            suggestedCorrection = etSuggestedCorrection.text.toString().trim(),
            details = details,
            timestamp = System.currentTimeMillis(),
            status = "pending"
        )

        btnSubmit.isEnabled = false
        ReportEditRepository.submit(submission) { error ->
            if (!isAdded) return@submit
            btnSubmit.isEnabled = true
            if (error == null) {
                showSuccessConfirmation()
            } else {
                Toast.makeText(requireContext(), "Could not submit report: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
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
        categoryInput.setText("", false)
        categoryLayout.error = null
        detailsLayout.error = null
        etSuggestedCorrection.text.clear()
        etDetails.text.clear()
    }

    override fun onDestroyView() {
        spotsListener?.remove()
        spotsListener = null
        super.onDestroyView()
    }
}
