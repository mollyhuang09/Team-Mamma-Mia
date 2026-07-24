package com.studypin.app.ui.review

import android.widget.ImageButton
import android.widget.TextView
import com.studypin.app.R
import com.studypin.app.data.ReviewRepository
import com.studypin.app.model.StudySpotReview

object ReviewHelpfulBinder {

    fun bind(button: ImageButton, countView: TextView, review: StudySpotReview) {
        fun render() {
            val selected = ReviewRepository.isHelpfulByCurrentUser(review.id)
            button.isSelected = selected
            button.contentDescription = button.context.getString(
                if (selected) R.string.remove_review_helpful else R.string.mark_review_helpful
            )
            countView.text = ReviewRepository.helpfulCountFor(review.id).toString()
        }

        render()
        button.setOnClickListener {
            ReviewRepository.toggleHelpful(review.id)
            render()
        }
    }
}
