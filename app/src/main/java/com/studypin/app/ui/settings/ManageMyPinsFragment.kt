package com.studypin.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.studypin.app.R
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.StudySpot
import com.studypin.app.model.SpotStatus
import com.studypin.app.utils.ImageUtils

class ManageMyPinsFragment : Fragment() {

    private lateinit var adapter: ManagePinsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_manage_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvManageListTitle).text = getString(R.string.pins_added)
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerManageList)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = ManagePinsAdapter(
            onRowClick = { spot ->
                val bundle = Bundle().apply { putString("spotId", spot.id) }
                findNavController().navigate(R.id.action_manageMyPins_to_spotDetail, bundle)
            },
            onDeleteClick = { spot -> confirmDelete(view, spot) }
        )
        recycler.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showEmpty(view, "Sign in to manage your pins")
            return
        }
        StudySpotRepository.spotsForUser(
            userId = uid,
            onSuccess = { spots ->
                if (!isAdded) return@spotsForUser
                val activeSpots = spots.filter { it.status != SpotStatus.REMOVED }
                adapter.submitList(activeSpots)
                if (activeSpots.isEmpty()) showEmpty(view, "You haven't added any pins yet") else hideEmpty(view)
            },
            onError = {
                if (isAdded) showEmpty(view, "Could not load your pins")
            }
        )
    }

    private fun confirmDelete(view: View, spot: StudySpot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete this pin?")
            .setMessage("This removes it from the map for everyone.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Delete") { _, _ ->
                StudySpotRepository.deleteSpot(spot.id) { success, error ->
                    if (!isAdded) return@deleteSpot
                    if (success) {
                        adapter.removeSpot(spot.id)
                        if (adapter.itemCount == 0) showEmpty(view, "You haven't added any pins yet")
                    } else {
                        Toast.makeText(requireContext(), error ?: "Could not delete pin", Toast.LENGTH_LONG).show()
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
}

class ManagePinsAdapter(
    private val onRowClick: (StudySpot) -> Unit,
    private val onDeleteClick: (StudySpot) -> Unit
) : RecyclerView.Adapter<ManagePinsAdapter.ViewHolder>() {

    private val spots = mutableListOf<StudySpot>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivManageSpotImage)
        val tvName: TextView = view.findViewById(R.id.tvManageSpotName)
        val tvAddress: TextView = view.findViewById(R.id.tvManageSpotAddress)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteManageSpot)
    }

    fun submitList(newSpots: List<StudySpot>) {
        spots.clear()
        spots.addAll(newSpots)
        notifyDataSetChanged()
    }

    fun removeSpot(spotId: String) {
        val index = spots.indexOfFirst { it.id == spotId }
        if (index != -1) {
            spots.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manage_spot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val spot = spots[position]
        holder.tvName.text = spot.name
        holder.tvAddress.text = spot.address
        val imageUri = (spot.imageUrls.firstOrNull() ?: spot.imageUrl)?.takeUnless { it.isBlank() }
        if (imageUri != null) {
            holder.ivImage.load(ImageUtils.toLoadableModel(imageUri)) {
                placeholder(R.drawable.photo_placeholder)
                crossfade(true)
            }
        } else {
            holder.ivImage.setImageResource(R.drawable.photo_placeholder)
        }
        holder.itemView.setOnClickListener { onRowClick(spot) }
        holder.btnDelete.setOnClickListener { onDeleteClick(spot) }
    }

    override fun getItemCount(): Int = spots.size
}
