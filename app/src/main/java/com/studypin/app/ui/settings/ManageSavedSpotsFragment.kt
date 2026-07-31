package com.studypin.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.studypin.app.R
import com.studypin.app.data.SavedSpotRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.StudySpot

class ManageSavedSpotsFragment : Fragment() {

    private lateinit var adapter: ManagePinsAdapter
    private var savedSpotsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_manage_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvManageListTitle).text = getString(R.string.saved_spots)
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerManageList)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = ManagePinsAdapter(
            onRowClick = { spot ->
                val bundle = Bundle().apply { putString("spotId", spot.id) }
                findNavController().navigate(R.id.action_manageSavedSpots_to_spotDetail, bundle)
            },
            onDeleteClick = { spot -> confirmUnsave(view, spot) }
        )
        recycler.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showEmpty(view, "Sign in to see your saved spots")
            return
        }

        savedSpotsListener = SavedSpotRepository.observeSavedSpotIds(
            userId = uid,
            onSuccess = { ids ->
                if (!isAdded) return@observeSavedSpotIds
                StudySpotRepository.getSpotsByIds(
                    ids = ids,
                    onSuccess = { spots ->
                        if (!isAdded) return@getSpotsByIds
                        adapter.submitList(spots)
                        if (spots.isEmpty()) showEmpty(view, "You haven't saved any spots yet") else hideEmpty(view)
                    },
                    onError = {
                        if (isAdded) showEmpty(view, "Could not load your saved spots")
                    }
                )
            },
            onError = {
                if (isAdded) showEmpty(view, "Could not load your saved spots")
            }
        )
    }

    private fun confirmUnsave(view: View, spot: StudySpot) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove from saved spots?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                SavedSpotRepository.unsaveSpot(uid, spot.id) { success, error ->
                    if (isAdded && !success) {
                        Toast.makeText(requireContext(), error ?: "Could not remove spot", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showEmpty(view: View, message: String) {
        view.findViewById<TextView>(R.id.tvManageListEmpty).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun hideEmpty(view: View) {
        view.findViewById<TextView>(R.id.tvManageListEmpty).visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savedSpotsListener?.remove()
        savedSpotsListener = null
    }
}
