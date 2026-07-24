package com.studypin.app.ui.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.StudySpot
import com.studypin.app.ui.filter.FilterFragment


class ListFragment : Fragment() {

    private lateinit var adapter: SpotListAdapter
    private lateinit var etSearch: EditText
    private var selectedAmenities: Set<String> = emptySet()
    private var selectedAvailableOnly = false
    private var selectedSortPosition = 0

    // Always sort/filter from the full original list, never from the
    // currently-displayed (already-filtered) list.
    private val allSpots: List<StudySpot> = MockData.studySpots

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etHeaderSearch)

        // set up header
        view.findViewById<TextView>(R.id.tvHeaderTitle).text = "List"
        view.findViewById<TextView>(R.id.tvHeaderSubtitle).text = "Find and pin the best study spot"
        view.findViewById<TextInputLayout>(R.id.layoutHeaderSearch).hint = "Search for location..."

        view.findViewById<View>(R.id.btnHeaderFilters).setOnClickListener {
            val arguments = Bundle().apply {
                putStringArrayList(
                    FilterFragment.KEY_AMENITIES,
                    ArrayList(selectedAmenities)
                )
                putBoolean(FilterFragment.KEY_AVAILABLE_ONLY, selectedAvailableOnly)
                putInt(FilterFragment.KEY_SORT_POSITION, selectedSortPosition)
            }
            findNavController().navigate(R.id.action_list_to_filter, arguments)
        }

        parentFragmentManager.setFragmentResultListener(
            FilterFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            selectedAmenities = result
                .getStringArrayList(FilterFragment.KEY_AMENITIES)
                ?.toSet()
                .orEmpty()
            selectedAvailableOnly = result.getBoolean(FilterFragment.KEY_AVAILABLE_ONLY)
            selectedSortPosition = result.getInt(FilterFragment.KEY_SORT_POSITION, 0)
            applyFiltersAndSort()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerSpots)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SpotListAdapter(allSpots) { spot ->
            val gemCount = MockData.hiddenGemCountFor(spot.id)
            if (gemCount > 0) {
                val bundle = Bundle().apply {
                    putString("parentSpotId", spot.id)
                }
                findNavController().navigate(R.id.action_list_to_spotGroup, bundle)
            } else {
                val bundle = Bundle().apply {
                    putString("spotId", spot.id)
                }
                findNavController().navigate(R.id.action_list_to_spotDetail, bundle)
            }
        }
        recyclerView.adapter = adapter

        setupSearchListener()
        applyFiltersAndSort()
    }

    private fun setupSearchListener() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                applyFiltersAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Re-runs search -> filter -> sort every time any control changes.
     * Simple and correct for prototype scale (10 mock spots);
     * not optimized for large datasets, but that's fine for now.
     */
    private fun applyFiltersAndSort() {
        var result = allSpots

        // Search by name (case-insensitive substring match)
        val query = etSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Filter by every selected amenity.
        if (selectedAmenities.isNotEmpty()) {
            result = result.filter { spot ->
                selectedAmenities.all { amenity -> spot.amenities.contains(amenity) }
            }
        }

        // Filter by occupancy
        if (selectedAvailableOnly) {
            result = result.filter { it.occupancyLabel() in listOf("Empty", "Available") }
        }

        // Sort
        result = when (selectedSortPosition) {
            0 -> result.sortedByDescending { it.avgRating }
            1 -> result.sortedByDescending { it.capacity.approxSeats - it.currentCheckIns }
            else -> result
        }

        adapter.updateList(result)
    }

}
