package com.studypin.app.ui.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.studypin.app.R

class ReportSuccessFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_report_success, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyStatusBarInset(view)

        view.findViewById<View>(R.id.btnCloseReportSuccess).setOnClickListener {
            returnToSpotOrMap()
        }
        view.findViewById<View>(R.id.btnBackToSpot).setOnClickListener {
            returnToSpotOrMap()
        }
        view.findViewById<View>(R.id.btnReturnToMap).setOnClickListener {
            val navController = findNavController()
            if (!navController.popBackStack(R.id.mapFragment, false)) {
                navController.navigate(R.id.mapFragment)
            }
        }
    }

    private fun applyStatusBarInset(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val statusBarInset =
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            root.updatePadding(top = statusBarInset + dpToPx(4))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun returnToSpotOrMap() {
        val navController = findNavController()
        if (!navController.popBackStack(R.id.spotDetailFragment, false)) {
            navController.navigate(R.id.mapFragment)
        }
    }
}
