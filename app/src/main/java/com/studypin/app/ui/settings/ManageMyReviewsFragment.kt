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
import com.studypin.app.R
import com.studypin.app.data.ReviewRepository
import com.studypin.app.data.StudySpotRepository
import com.studypin.app.model.StudySpotReview

class ManageMyReviewsFragment : Fragment() {

    private lateinit var adapter: ManageReviewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_manage_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvManageListTitle).text = getString(R.string.my_reviews)
        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerManageList)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = ManageReviewsAdapter(
            onRowClick = { review ->
                val bundle = Bundle().apply { putString("spotId", review.spotId) }
                findNavController().navigate(R.id.action_manageMyReviews_to_spotDetail, bundle)
            },
            onDeleteClick = { review -> confirmDelete(view, review) }
        )
        recycler.adapter = adapter

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showEmpty(view, "Sign in to manage your reviews")
            return
        }
        loadReviews(view, uid)
    }

    private fun loadReviews(view: View, uid: String) {
        ReviewRepository.reviewsByUser(
            userId = uid,
            onSuccess = { reviews ->
                if (!isAdded) return@reviewsByUser
                if (reviews.isEmpty()) {
                    adapter.submitList(reviews, emptyMap())
                    showEmpty(view, "You haven't written any reviews yet")
                    return@reviewsByUser
                }
                StudySpotRepository.getSpotsByIds(
                    ids = reviews.map { it.spotId }.distinct(),
                    onSuccess = { spots ->
                        if (!isAdded) return@getSpotsByIds
                        adapter.submitList(reviews, spots.associate { it.id to it.name })
                        hideEmpty(view)
                    },
                    onError = {
                        if (isAdded) {
                            adapter.submitList(reviews, emptyMap())
                            hideEmpty(view)
                        }
                    }
                )
            },
            onError = {
                if (isAdded) showEmpty(view, "Could not load your reviews")
            }
        )
    }

    private fun confirmDelete(view: View, review: StudySpotReview) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete this review?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Delete") { _, _ ->
                ReviewRepository.deleteReview(review.spotId, review.id) { success, error ->
                    if (!isAdded) return@deleteReview
                    if (success) {
                        adapter.removeReview(review.id)
                        if (adapter.itemCount == 0) showEmpty(view, "You haven't written any reviews yet")
                    } else {
                        Toast.makeText(requireContext(), error ?: "Could not delete review", Toast.LENGTH_LONG).show()
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

class ManageReviewsAdapter(
    private val onRowClick: (StudySpotReview) -> Unit,
    private val onDeleteClick: (StudySpotReview) -> Unit
) : RecyclerView.Adapter<ManageReviewsAdapter.ViewHolder>() {

    private val reviews = mutableListOf<StudySpotReview>()
    private var spotNames: Map<String, String> = emptyMap()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSpotName: TextView = view.findViewById(R.id.tvManageReviewSpotName)
        val tvRating: TextView = view.findViewById(R.id.tvManageReviewRating)
        val tvText: TextView = view.findViewById(R.id.tvManageReviewText)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteManageReview)
    }

    fun submitList(newReviews: List<StudySpotReview>, newSpotNames: Map<String, String>) {
        reviews.clear()
        reviews.addAll(newReviews)
        spotNames = newSpotNames
        notifyDataSetChanged()
    }

    fun removeReview(reviewId: String) {
        val index = reviews.indexOfFirst { it.id == reviewId }
        if (index != -1) {
            reviews.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manage_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = reviews[position]
        holder.tvSpotName.text = spotNames[review.spotId] ?: review.spotId
        holder.tvRating.text = "★".repeat(review.overallRating.coerceIn(0, 5))
        holder.tvText.apply {
            text = review.reviewText
            visibility = if (review.reviewText.isBlank()) View.GONE else View.VISIBLE
        }
        holder.itemView.setOnClickListener { onRowClick(review) }
        holder.btnDelete.setOnClickListener { onDeleteClick(review) }
    }

    override fun getItemCount(): Int = reviews.size
}
