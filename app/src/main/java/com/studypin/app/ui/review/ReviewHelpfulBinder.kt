package com.studypin.app.ui.review

import android.widget.ImageButton
import android.widget.TextView
import com.studypin.app.R
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpotReview
import java.util.Locale

object ReviewHelpfulBinder {

    fun bind(button: ImageButton, countView: TextView, review: StudySpotReview) {
        fun render() {
            val selected = ReviewRepository.isHelpfulByCurrentUser(review.id)
            button.isSelected = selected
            button.contentDescription = button.context.getString(
                if (selected) R.string.remove_review_helpful else R.string.mark_review_helpful
            )
            countView.text = formatHelpfulCount(ReviewRepository.helpfulCountFor(review.id))
        }

        render()
        button.setOnClickListener {
            ReviewRepository.toggleHelpful(review.id)
            render()
        }
    }

    private fun formatHelpfulCount(count: Int): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> {
            val value = count / 1_000.0
            if (value % 1.0 == 0.0) {
                "${value.toInt()}k"
            } else {
                String.format(Locale.CANADA, "%.1fk", value)
            }
        }
        else -> {
            val value = count / 1_000_000.0
            if (value % 1.0 == 0.0) {
                "${value.toInt()}M"
            } else {
                String.format(Locale.CANADA, "%.1fM", value)
            }
        }
    }
}
