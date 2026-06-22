package com.studypin.app.ui.review

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object StarRatingViews {

    private const val MAX_STARS = 5
    private const val SELECTED_COLOR = "#F4B400"
    private const val UNSELECTED_COLOR = "#CFCFCF"

    fun buildStarRow(
        context: Context,
        selectedRating: Int,
        enabled: Boolean = true,
        starSizeSp: Float = 28f,
        onStarSelected: ((Int) -> Unit)? = null
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            for (index in 1..MAX_STARS) {
                addView(
                    createStarTextView(
                        context = context,
                        index = index,
                        selectedRating = selectedRating,
                        enabled = enabled,
                        starSizeSp = starSizeSp,
                        onStarSelected = onStarSelected
                    )
                )
                if (index < MAX_STARS) {
                    addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(context, 4), 1)
                    })
                }
            }
        }
    }

    private fun createStarTextView(
        context: Context,
        index: Int,
        selectedRating: Int,
        enabled: Boolean,
        starSizeSp: Float,
        onStarSelected: ((Int) -> Unit)?
    ): TextView {
        return TextView(context).apply {
            text = if (index <= selectedRating) "★" else "☆"
            setTextColor(Color.parseColor(if (index <= selectedRating) SELECTED_COLOR else UNSELECTED_COLOR))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, starSizeSp)
            gravity = Gravity.CENTER
            minWidth = dpToPx(context, (starSizeSp * 1.1).toInt())
            minHeight = dpToPx(context, (starSizeSp * 1.1).toInt())
            setPadding(0, 0, 0, 0)
            contentDescription = "$index out of 5 stars"

            if (enabled && onStarSelected != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onStarSelected(index) }
            }
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

