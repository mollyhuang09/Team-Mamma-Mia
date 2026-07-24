package com.studypin.app.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.studypin.app.R
import com.studypin.app.model.StudySpot

class SpotListAdapter(
    private var spots: List<StudySpot>,
    private val showHiddenGemBadge: Boolean = true,
    private val onSpotClick: (StudySpot) -> Unit
) : RecyclerView.Adapter<SpotListAdapter.SpotViewHolder>() {

    private var allSpots: List<StudySpot> = spots

    inner class SpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSpotName)
        val tvOccupancy: TextView = view.findViewById(R.id.tvOccupancyLabel)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvAmenities: TextView = view.findViewById(R.id.tvAmenities)
        val tvHiddenGemBadge: TextView = view.findViewById(R.id.tvHiddenGemBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): SpotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_study_spot, parent, false)
        return SpotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val spot = spots[position]
        holder.tvName.text = if (spot.isHiddenGem) "${spot.name} 💎" else spot.name
        holder.tvOccupancy.text = spot.occupancyLabel()
        holder.tvRating.text = "★ ${spot.avgRating} (${spot.totalRatings})"
        holder.tvAmenities.text = spot.amenities.joinToString(" · ")

        val gemCount = if (showHiddenGemBadge) {
            allSpots.count { it.parentSpotId == spot.id }
        } else 0
        if (gemCount > 0) {
            holder.tvHiddenGemBadge.text = "💎 $gemCount hidden gem${if (gemCount > 1) "s" else ""}"
            holder.tvHiddenGemBadge.visibility = View.VISIBLE
        } else {
            holder.tvHiddenGemBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onSpotClick(spot)
        }
    }

    override fun getItemCount(): Int = spots.size

    /** Replace the currently displayed list (used after search/filter/sort). */
    fun updateList(newSpots: List<StudySpot>, allSpots: List<StudySpot> = newSpots) {
        spots = newSpots
        this.allSpots = allSpots
        notifyDataSetChanged()
    }
}
