package com.studypin.app.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.ui.list.SpotListAdapter

class SpotGroupFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_spot_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parentSpotId = arguments?.getString("parentSpotId") ?: return

        val parent = MockData.studySpots.firstOrNull { it.id == parentSpotId } ?: return
        val hiddenGems = MockData.hiddenGemsFor(parentSpotId)

        // Parent spot shown first, followed by its hidden gems.
        val groupList = listOf(parent) + hiddenGems

        view.findViewById<TextView>(R.id.tvGroupHeader).text = parent.name

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerGroupSpots)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Reusing SpotListAdapter since both rows look the same (no special labeling).
        // Tapping any row here always goes straight to Detail — no further nesting.
        recyclerView.adapter = SpotListAdapter(groupList, showHiddenGemBadge = false) { tappedSpot ->
            val bundle = Bundle().apply {
                putString("spotId", tappedSpot.id)
            }
            findNavController().navigate(R.id.action_spotGroup_to_spotDetail, bundle)
        }
    }
}