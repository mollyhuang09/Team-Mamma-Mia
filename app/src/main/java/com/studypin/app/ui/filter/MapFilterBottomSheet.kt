package com.studypin.app.ui.filter

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.studypin.app.R

class MapFilterBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "map_filter_bottom_sheet"
        const val RESULT_KEY = "map_filter_result"
        const val KEY_AMENITIES = "map_filter_amenities"
        const val KEY_ENVIRONMENTS = "map_filter_environments"
        const val KEY_AVAILABLE_ONLY = "map_filter_available_only"

        private const val ARG_AMENITIES = "arg_amenities"
        private const val ARG_ENVIRONMENTS = "arg_environments"
        private const val ARG_AVAILABLE_ONLY = "arg_available_only"

        fun newInstance(
            amenities: Set<String>,
            environments: Set<String>,
            availableOnly: Boolean
        ): MapFilterBottomSheet = MapFilterBottomSheet().apply {
            arguments = Bundle().apply {
                putStringArrayList(ARG_AMENITIES, ArrayList(amenities))
                putStringArrayList(ARG_ENVIRONMENTS, ArrayList(environments))
                putBoolean(ARG_AVAILABLE_ONLY, availableOnly)
            }
        }
    }

    private val amenityChips = linkedMapOf(
        R.id.chipMapWifi to "wifi",
        R.id.chipMapOutlets to "outlets",
        R.id.chipMapQuiet to "quiet",
        R.id.chipMapWashroom to "washroom",
        R.id.chipMapFood to "food",
        R.id.chipMapParking to "parking"
    )

    private val environmentChips = linkedMapOf(
        R.id.chipMapIndoor to "indoor",
        R.id.chipMapOutdoor to "outdoor",
        R.id.chipMapGroupFriendly to "group_friendly"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_map_filters, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener

            bottomSheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(bottomSheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedAmenities = arguments
            ?.getStringArrayList(ARG_AMENITIES)
            ?.toMutableSet()
            ?: mutableSetOf()
        val selectedEnvironments = arguments
            ?.getStringArrayList(ARG_ENVIRONMENTS)
            ?.toMutableSet()
            ?: mutableSetOf()
        val availableOnlyChip = view.findViewById<Chip>(R.id.chipMapAvailableOnly)

        amenityChips.forEach { (chipId, key) ->
            val chip = view.findViewById<Chip>(chipId)
            chip.isChecked = key in selectedAmenities
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedAmenities.add(key) else selectedAmenities.remove(key)
                updateApplyButton(view, selectedAmenities, selectedEnvironments, availableOnlyChip)
            }
        }

        environmentChips.forEach { (chipId, key) ->
            val chip = view.findViewById<Chip>(chipId)
            chip.isChecked = key in selectedEnvironments
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedEnvironments.add(key) else selectedEnvironments.remove(key)
                updateApplyButton(view, selectedAmenities, selectedEnvironments, availableOnlyChip)
            }
        }

        availableOnlyChip.isChecked = arguments?.getBoolean(ARG_AVAILABLE_ONLY) ?: false
        availableOnlyChip.setOnCheckedChangeListener { _, _ ->
            updateApplyButton(view, selectedAmenities, selectedEnvironments, availableOnlyChip)
        }

        view.findViewById<TextView>(R.id.btnClearMapFilters).setOnClickListener {
            amenityChips.keys.forEach { chipId ->
                view.findViewById<Chip>(chipId).isChecked = false
            }
            environmentChips.keys.forEach { chipId ->
                view.findViewById<Chip>(chipId).isChecked = false
            }
            availableOnlyChip.isChecked = false
            selectedAmenities.clear()
            selectedEnvironments.clear()

            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putStringArrayList(KEY_AMENITIES, arrayListOf())
                    putStringArrayList(KEY_ENVIRONMENTS, arrayListOf())
                    putBoolean(KEY_AVAILABLE_ONLY, false)
                }
            )
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnApplyMapFilters).setOnClickListener {
            parentFragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putStringArrayList(KEY_AMENITIES, ArrayList(selectedAmenities))
                    putStringArrayList(KEY_ENVIRONMENTS, ArrayList(selectedEnvironments))
                    putBoolean(KEY_AVAILABLE_ONLY, availableOnlyChip.isChecked)
                }
            )
            dismiss()
        }

        view.findViewById<TextView>(R.id.btnCancelMapFilters).setOnClickListener {
            dismiss()
        }

        updateApplyButton(view, selectedAmenities, selectedEnvironments, availableOnlyChip)
    }

    private fun updateApplyButton(
        view: View,
        selectedAmenities: Set<String>,
        selectedEnvironments: Set<String>,
        availableOnlyChip: Chip
    ) {
        val enabled = selectedAmenities.isNotEmpty() ||
            selectedEnvironments.isNotEmpty() ||
            availableOnlyChip.isChecked
        val button = view.findViewById<MaterialButton>(R.id.btnApplyMapFilters)
        button.isEnabled = enabled
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (enabled) R.color.brand_main else R.color.button_disabled
            )
        )
    }
}
