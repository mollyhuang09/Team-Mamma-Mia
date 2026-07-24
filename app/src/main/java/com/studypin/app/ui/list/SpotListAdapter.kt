package com.studypin.app.ui.list

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.studypin.app.R
import com.studypin.app.data.MockData
import com.studypin.app.model.StudySpot
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SpotListAdapter(
    private var spots: List<StudySpot>,
    private val showHiddenGemBadge: Boolean = true,
    private val onSpotClick: (StudySpot) -> Unit
) : RecyclerView.Adapter<SpotListAdapter.SpotViewHolder>() {

    inner class SpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSpotName)
        val tvOccupancy: TextView = view.findViewById(R.id.tvOccupancyLabel)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvHiddenGemBadge: TextView = view.findViewById(R.id.tvHiddenGemBadge)
        val tvHiddenGemBlock: LinearLayout = view.findViewById(R.id.tvHiddenGemBlock)
        val chipGroupTags: ChipGroup = view.findViewById(R.id.chipGroupTags)
        val tvSpotAddress: TextView = view.findViewById(R.id.tvSpotAddress)
        val ivSpotImage: ImageView = view.findViewById(R.id.ivSpotImage)
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
        holder.tvRating.text = "${spot.avgRating} (${spot.totalRatings})"
        if (spot.address != "") {
            holder.tvSpotAddress.text = spot.address
            holder.tvSpotAddress.visibility = View.VISIBLE
        } else {
            holder.tvSpotAddress.visibility = View.GONE
        }
        val imageUri = spot.imageUrl
            ?.takeUnless { it.isBlank() || it == "placeholder_uri" }
        if (imageUri != null) {
            holder.ivSpotImage.setImageURI(Uri.parse(imageUri))
        } else {
            // placeholder - TBC
            holder.ivSpotImage.setImageResource(R.drawable.ic_launcher_background)
        }

        val gemCount = if (showHiddenGemBadge) MockData.hiddenGemCountFor(spot.id) else 0
        if (gemCount > 0) {
            holder.tvHiddenGemBadge.text = "💎 $gemCount hidden gem${if (gemCount > 1) "s" else ""}"
            holder.tvHiddenGemBlock.visibility = View.VISIBLE
        } else {
            holder.tvHiddenGemBlock.visibility = View.GONE
        }

        holder.chipGroupTags.removeAllViews()

        spot.amenities.forEach { amenity ->
            val chip = LayoutInflater.from(holder.itemView.context)
                .inflate(
                    R.layout.item_spot_tag,
                    holder.chipGroupTags,
                    false
                ) as Chip
            chip.isCheckable = false
            chip.isClickable = false
            chip.isFocusable = false
            chip.text = amenity.replaceFirstChar { it.uppercase() }

            holder.chipGroupTags.addView(chip)
        }

        holder.itemView.setOnClickListener {
            onSpotClick(spot)
        }
    }

    override fun getItemCount(): Int = spots.size

    /** Replace the currently displayed list (used after search/filter/sort). */
    fun updateList(newSpots: List<StudySpot>) {
        spots = newSpots
        notifyDataSetChanged()
    }
}