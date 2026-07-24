package com.studypin.app.ui.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.StudySpot


class ListFragment : Fragment() {

    private lateinit var adapter: SpotListAdapter
    private lateinit var etSearch: EditText
    private lateinit var cbWifi: CheckBox
    private lateinit var cbOutlets: CheckBox
    private lateinit var cbAvailableOnly: CheckBox
    private lateinit var sortSpinner: Spinner

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
        cbWifi = view.findViewById(R.id.cbWifi)
        cbOutlets = view.findViewById(R.id.cbOutlets)
        cbAvailableOnly = view.findViewById(R.id.cbAvailableOnly)
        sortSpinner = view.findViewById(R.id.sortSpinner)

        // set up header
        view.findViewById<TextView>(R.id.tvHeaderTitle).text = "List"
        view.findViewById<TextView>(R.id.tvHeaderSubtitle).text = "Find and pin the best study spot"
        view.findViewById<TextInputLayout>(R.id.layoutHeaderSearch).hint = "Search for location..."

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

        setupSortSpinner()
        setupSearchListener()
        setupFilterListeners()

        applyFiltersAndSort()
    }

    private fun setupSortSpinner() {
        val options = listOf("Sort: Rating (high to low)", "Sort: Occupancy (available first)")
        sortSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            options
        )
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applyFiltersAndSort()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
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

    private fun setupFilterListeners() {
        cbWifi.setOnClickListener { applyFiltersAndSort() }
        cbOutlets.setOnClickListener { applyFiltersAndSort() }
        cbAvailableOnly.setOnClickListener { applyFiltersAndSort() }
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

        // Filter by amenities
        if (cbWifi.isChecked) {
            result = result.filter { it.amenities.contains("wifi") }
        }
        if (cbOutlets.isChecked) {
            result = result.filter { it.amenities.contains("outlets") }
        }

        // Filter by occupancy
        if (cbAvailableOnly.isChecked) {
            result = result.filter { it.occupancyLabel() in listOf("Empty", "Available") }
        }

        // Sort
        result = when (sortSpinner.selectedItemPosition) {
            0 -> result.sortedByDescending { it.avgRating }
            1 -> result.sortedByDescending { it.capacity.approxSeats - it.currentCheckIns }
            else -> result
        }

        adapter.updateList(result)
    }

}