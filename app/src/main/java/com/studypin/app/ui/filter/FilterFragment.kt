package com.studypin.app.ui.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.button.MaterialButton
import com.studypin.app.R

class FilterFragment : Fragment() {

    private class SortOptionAdapter(
        context: android.content.Context,
        options: List<String>,
        private var selectedPosition: Int
    ) : ArrayAdapter<String>(
        context,
        R.layout.item_dropdown_option,
        R.id.tvDropdownOption,
        options
    ) {
        init {
            setDropDownViewResource(R.layout.item_dropdown_option)
        }

        fun setSelectedPosition(position: Int) {
            selectedPosition = position
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return styledView(super.getView(position, convertView, parent), position)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return styledView(super.getDropDownView(position, convertView, parent), position)
        }

        private fun styledView(view: View, position: Int): View {
            val row = view as LinearLayout
            val isSelected = position == selectedPosition
            row.setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_dropdown_item_selected
                } else {
                    android.R.color.transparent
                }
            )
            row.findViewById<ImageView>(R.id.ivDropdownCheck).visibility =
                if (isSelected) View.VISIBLE else View.GONE
            return row
        }
    }

    companion object {
        const val RESULT_KEY = "filter_result"
        const val KEY_AMENITIES = "filter_amenities"
        const val KEY_AVAILABLE_ONLY = "filter_available_only"
        const val KEY_SORT_POSITION = "filter_sort_position"
    }

    private val sortOptions = listOf(
        "Rating (high to low)",
        "Most reviews",
        "Recently added"
    )

    private val amenityChips = mapOf(
        R.id.chipFilterWifi to "wifi",
        R.id.chipFilterOutlets to "outlets",
        R.id.chipFilterPrinting to "printing",
        R.id.chipFilterQuiet to "quiet",
        R.id.chipFilterWashroom to "washroom",
        R.id.chipFilterFood to "food",
        R.id.chipFilterParking to "parking"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_filter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialAmenities = arguments
            ?.getStringArrayList(KEY_AMENITIES)
            ?.toSet()
            .orEmpty()

        amenityChips.forEach { (chipId, amenity) ->
            view.findViewById<Chip>(chipId).isChecked = amenity in initialAmenities
        }

        val availableOnlyChip = view.findViewById<Chip>(R.id.chipFilterAvailableOnly)
        availableOnlyChip.isChecked = arguments?.getBoolean(KEY_AVAILABLE_ONLY) ?: false

        val sortDropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.dropdownFilterSort)
        var selectedSortPosition = arguments?.getInt(KEY_SORT_POSITION, 0) ?: 0
        selectedSortPosition = selectedSortPosition.coerceIn(sortOptions.indices)
        val sortAdapter = SortOptionAdapter(
            requireContext(),
            sortOptions,
            selectedSortPosition
        )
        sortDropdown.setAdapter(sortAdapter)
        sortDropdown.setText(sortOptions[selectedSortPosition], false)
        sortDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSortPosition = position
            sortAdapter.setSelectedPosition(position)
        }

        view.findViewById<View>(R.id.btnCloseFilters).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<MaterialButton>(R.id.btnApplyFilters).setOnClickListener {
            val selectedAmenities = ArrayList(
                amenityChips
                    .filter { (chipId, _) -> view.findViewById<Chip>(chipId).isChecked }
                    .values
            )

            val result = Bundle().apply {
                putStringArrayList(KEY_AMENITIES, selectedAmenities)
                putBoolean(KEY_AVAILABLE_ONLY, availableOnlyChip.isChecked)
                putInt(KEY_SORT_POSITION, selectedSortPosition)
            }

            parentFragmentManager.setFragmentResult(RESULT_KEY, result)
            findNavController().navigateUp()
        }
    }
}
