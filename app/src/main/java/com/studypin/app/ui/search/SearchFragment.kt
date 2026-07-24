package com.studypin.app.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.StudySpot
import com.studypin.app.ui.toTagLabel

class SearchFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "search_result"
        const val KEY_QUERY = "search_query"
        const val KEY_INITIAL_QUERY = "search_initial_query"
    }

    private lateinit var searchInput: EditText
    private lateinit var recentAdapter: RecentSearchAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var recentSection: View
    private lateinit var suggestedTitle: View

    private val recentSearches = mutableListOf(
        "Dana Porter Library",
        "SLC Study Lounge",
        "Conrad Grebel Reading Room"
    )

    // Include nested hidden gems so the search result can show their badge.
    private val suggestedSpots = MockData.studySpots

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.etSearchScreen)
        recentSection = view.findViewById(R.id.layoutRecentSearches)
        suggestedTitle = view.findViewById(R.id.tvSuggestedTitle)

        val initialQuery = arguments?.getString(KEY_INITIAL_QUERY).orEmpty()
        searchInput.setText(initialQuery)
        searchInput.setSelection(searchInput.text.length)

        val recentRecycler = view.findViewById<RecyclerView>(R.id.rvRecentSearches)
        recentAdapter = RecentSearchAdapter(recentSearches) { query ->
            chooseSearch(query)
        }
        recentRecycler.layoutManager = LinearLayoutManager(requireContext())
        recentRecycler.adapter = recentAdapter

        val suggestionRecycler = view.findViewById<RecyclerView>(R.id.rvSuggestedSpots)
        suggestionAdapter = SuggestionAdapter { spot ->
            chooseSearch(spot.name)
        }
        suggestionRecycler.layoutManager = LinearLayoutManager(requireContext())
        suggestionRecycler.adapter = suggestionAdapter

        view.findViewById<View>(R.id.btnCancelSearch).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnClearRecentSearches).setOnClickListener {
            recentSearches.clear()
            recentAdapter.updateItems(recentSearches)
            updateRecentVisibility()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSuggestions(s?.toString().orEmpty())
                updateRecentVisibility()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        updateSuggestions(initialQuery)
        updateRecentVisibility()

        searchInput.requestFocus()
        searchInput.post {
            val inputMethodManager = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateSuggestions(query: String) {
        val trimmedQuery = query.trim()
        val matches = suggestedSpots.filter { spot ->
            trimmedQuery.isEmpty() ||
                spot.name.contains(trimmedQuery, ignoreCase = true) ||
                spot.address.contains(trimmedQuery, ignoreCase = true)
        }
        suggestionAdapter.updateItems(matches)
    }

    private fun updateRecentVisibility() {
        val hasNoQuery = searchInput.text.toString().trim().isEmpty()
        recentSection.visibility = if (hasNoQuery && recentSearches.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        suggestedTitle.visibility = if (hasNoQuery) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun chooseSearch(query: String) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply { putString(KEY_QUERY, query) }
        )
        findNavController().navigateUp()
    }

    private class RecentSearchAdapter(
        private var items: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvRecentSearchName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val query = items[position]
            holder.name.text = query
            holder.itemView.setOnClickListener { onClick(query) }
        }

        override fun getItemCount(): Int = items.size

        fun updateItems(newItems: List<String>) {
            items = newItems.toList()
            notifyDataSetChanged()
        }
    }

    private class SuggestionAdapter(
        private val onClick: (StudySpot) -> Unit
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        private var items: List<StudySpot> = emptyList()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvSuggestionName)
            val secretGemBadge: TextView = view.findViewById(R.id.tvHiddenGemBadge)
            val tags: ChipGroup = view.findViewById(R.id.chipGroupTags)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val spot = items[position]
            holder.name.text = spot.name
            if (spot.isHiddenGem) {
                holder.secretGemBadge.visibility = View.VISIBLE
            } else {
                holder.secretGemBadge.visibility = View.GONE
            }
            holder.tags.removeAllViews()

            spot.amenities
                .map { amenity -> amenity.toTagLabel() }
                .take(3)
                .forEach { label ->
                    val chip = LayoutInflater.from(holder.itemView.context)
                        .inflate(R.layout.item_spot_tag, holder.tags, false) as Chip
                    chip.text = label
                    chip.isClickable = false
                    chip.isFocusable = false
                    holder.tags.addView(chip)
                }

            holder.itemView.setOnClickListener { onClick(spot) }
        }

        override fun getItemCount(): Int = items.size

        fun updateItems(newItems: List<StudySpot>) {
            items = newItems
            notifyDataSetChanged()
        }

    }
}
