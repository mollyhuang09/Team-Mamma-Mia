package com.studypin.app.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpotReview
import java.util.Locale

class ReviewListFragment : Fragment() {

    private var spotId: String = ""
    private lateinit var adapter: ReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_review_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spotId = arguments?.getString("spotId") ?: ""
        val spot = MockData.studySpots.firstOrNull { it.id == spotId }
        val stats = ReviewRepository.displayStatsForSpot(spot ?: return)

        view.findViewById<TextView>(R.id.tvReviewSpotName).text = spot.name
        view.findViewById<TextView>(R.id.tvReviewAverage).text = String.format(Locale.CANADA, "%.1f", stats.averageOverall)
        view.findViewById<TextView>(R.id.tvReviewCount).text = "${stats.reviewCount} reviews"

        val amenityLayout = view.findViewById<View>(R.id.layoutReviewAmenitySummary)
        val toggleBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnToggleAmenities)
        
        toggleBtn.setOnClickListener {
            if (amenityLayout.visibility == View.VISIBLE) {
                amenityLayout.visibility = View.GONE
                toggleBtn.text = "View details"
            } else {
                amenityLayout.visibility = View.VISIBLE
                toggleBtn.text = "Hide details"
            }
        }

        setupAmenitySummary(view, stats.amenityAverages)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerReviews)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ReviewAdapter(ReviewRepository.reviewsForSpot(spotId))
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btnWriteReview).setOnClickListener {
            val bundle = Bundle().apply {
                putString("spotId", spotId)
            }
            findNavController().navigate(R.id.action_reviewList_to_addReview, bundle)
        }
        
        if (ReviewRepository.hasUserReviewedSpot("You", spotId)) {
            view.findViewById<View>(R.id.btnWriteReview).visibility = View.GONE
        }
        
        if (adapter.itemCount == 0) {
            view.findViewById<View>(R.id.tvEmptyReviews).visibility = View.VISIBLE
        }
    }

    private fun setupAmenitySummary(view: View, averages: Map<String, Double>) {
        val layout = view.findViewById<LinearLayout>(R.id.layoutReviewAmenitySummary)
        layout.removeAllViews()

        averages.forEach { (amenity, avg) ->
            val label = when (amenity) {
                "noise" -> "Noise Level"
                "seating" -> "Seating Comfort"
                "wifi" -> "WiFi Strength"
                "outlets" -> "Outlet Availability"
                else -> amenity.replaceFirstChar { it.uppercase() }
            }
            val row = TextView(requireContext()).apply {
                text = "$label: " + String.format(Locale.CANADA, "%.1f / 5", avg)
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
            // Actually, let's just use the secondary text color
            row.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            layout.addView(row)
        }
    }
}

class ReviewAdapter(private val reviews: List<StudySpotReview>) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.tvReviewerAvatar)
        val name: TextView = view.findViewById(R.id.tvReviewerName)
        val date: TextView = view.findViewById(R.id.tvReviewSubmittedAt)
        val text: TextView = view.findViewById(R.id.tvReviewText)
        val meta: TextView = view.findViewById(R.id.tvReviewMeta)
        val stars: LinearLayout = view.findViewById(R.id.llReviewStars)
        val helpfulButton: ImageButton = view.findViewById(R.id.btnHelpful)
        val helpfulCount: TextView = view.findViewById(R.id.tvHelpfulCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = reviews[position]
        holder.avatar.text = review.reviewerName.firstOrNull()?.uppercase() ?: "?"
        holder.name.text = review.reviewerName
        holder.date.text = review.submittedAtLabel
        holder.text.text = review.reviewText
        
        val metaInfo = mutableListOf<String>()
        if (review.visitTimeOfDay.isNotEmpty()) metaInfo.add("Visited in ${review.visitTimeOfDay}")
        if (review.crowdLevel.isNotEmpty()) metaInfo.add(review.crowdLevel)
        holder.meta.text = metaInfo.joinToString(" • ")
        holder.meta.visibility = if (metaInfo.isEmpty()) View.GONE else View.VISIBLE

        holder.stars.removeAllViews()
        holder.stars.addView(StarRatingViews.buildStarRow(holder.itemView.context, review.overallRating, false, 16f))

        ReviewHelpfulBinder.bind(holder.helpfulButton, holder.helpfulCount, review)
    }

    override fun getItemCount() = reviews.size
}
