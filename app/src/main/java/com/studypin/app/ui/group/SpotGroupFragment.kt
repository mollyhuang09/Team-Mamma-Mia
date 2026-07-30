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
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.ui.list.SpotListAdapter
import com.studypin.app.ui.applyStatusBarInset
import android.widget.Toast

class SpotGroupFragment : Fragment() {

    private var spotsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_spot_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.applyStatusBarInset(extraTopDp = 4)
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val parentSpotId = arguments?.getString("parentSpotId") ?: return

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerGroupSpots)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = SpotListAdapter(emptyList(), showHiddenGemBadge = false) { tappedSpot ->
            val bundle = Bundle().apply {
                putString("spotId", tappedSpot.id)
            }
            findNavController().navigate(R.id.action_spotGroup_to_spotDetail, bundle)
        }
        recyclerView.adapter = adapter

        spotsListener = StudySpotRepository.observeSpots(
            onSuccess = { spots ->
                if (!isAdded) return@observeSpots
                val parent = spots.firstOrNull { it.id == parentSpotId }
                if (parent == null) {
                    Toast.makeText(requireContext(), "Study spot unavailable", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                    return@observeSpots
                }
                val groupList = listOf(parent) + spots.filter { it.parentSpotId == parentSpotId }
                view.findViewById<TextView>(R.id.tvGroupHeader).text = parent.name
                adapter.updateList(groupList, spots)
            },
            onError = { error ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Could not load study spot: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }


    override fun onDestroyView() {
        spotsListener?.remove()
        spotsListener = null
        super.onDestroyView()
    }
}
