package com.studypin.app.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.studypin.app.R
import com.studypin.app.data.ReportEditRepository
import com.studypin.app.model.ReportEditSubmission
import com.studypin.app.ui.applyStatusBarInset

class ReportFlagFragment : Fragment() {
    private val defaultSpotId = "spot_mock_report"
    private val defaultSpotName = "Mock Study Spot"

    private lateinit var categoryGroup: RadioGroup
    private lateinit var detailsLayout: TextInputLayout
    private lateinit var etDetails: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnClose: View

    private lateinit var spotId: String
    private lateinit var spotName: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_report_flag, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryGroup = view.findViewById(R.id.rgReportCategory)
        detailsLayout = view.findViewById(R.id.tilReportDetails)
        etDetails = view.findViewById(R.id.etReportDetails)
        btnSubmit = view.findViewById(R.id.btnSubmitReport)
        btnClose = view.findViewById(R.id.btnCloseReport)

        view.applyStatusBarInset(extraTopDp = 8)
        setupReportTarget()

        btnSubmit.setOnClickListener { submitReport() }
        btnClose.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupReportTarget() {
        spotId = arguments?.getString("spotId").orEmpty()
            .ifBlank { defaultSpotId }
        spotName = arguments?.getString("spotName").orEmpty()
            .ifBlank { defaultSpotName }
    }

    private fun submitReport() {
        detailsLayout.error = null

        val checkedId = categoryGroup.checkedRadioButtonId
        if (checkedId == -1) {
            Toast.makeText(requireContext(), "Choose a report category", Toast.LENGTH_SHORT).show()
            return
        }

        val category = view?.findViewById<RadioButton>(checkedId)?.text?.toString().orEmpty()
        val details = etDetails.text.toString().trim()

        val submission = ReportEditSubmission(
            spotId = spotId,
            spotName = spotName,
            category = category,
            suggestedCorrection = "",
            details = details,
            timestamp = System.currentTimeMillis(),
            status = "pending"
        )

        ReportEditRepository.submit(submission)

        val bundle = Bundle().apply {
            putString("spotId", spotId)
        }
        findNavController().navigate(R.id.action_reportFlag_to_reportSuccess, bundle)
    }
}
