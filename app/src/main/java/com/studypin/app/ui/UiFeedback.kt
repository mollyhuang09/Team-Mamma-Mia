package com.studypin.app.ui

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.studypin.app.R

object UiFeedback {
    fun show(anchor: View, message: String, long: Boolean = false) {
        val duration = if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        val snackbar = Snackbar.make(anchor, message, duration)
        snackbar.view.setBackgroundColor(ContextCompat.getColor(anchor.context, R.color.brand_main))
        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.surface_color))
            maxLines = 4
            textSize = 16f
        }
        snackbar.show()
    }
}

fun androidx.fragment.app.Fragment.showMessage(message: String, long: Boolean = false) =
    UiFeedback.show(requireView(), message, long)

fun android.app.Activity.showMessage(message: String, long: Boolean = false) =
    UiFeedback.show(findViewById(android.R.id.content), message, long)

/** Falls back silently if this Context isn't backed by an Activity (no attached window to anchor to). */
fun android.content.Context.showMessage(message: String, long: Boolean = false) {
    (this as? android.app.Activity)?.showMessage(message, long)
}
