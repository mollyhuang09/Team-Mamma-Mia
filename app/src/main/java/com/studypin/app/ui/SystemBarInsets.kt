package com.studypin.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Runs a callback with the device's current status-bar height. */
fun View.setOnApplyStatusBarInsetsListener(
    onInsetsApplied: (statusBarHeight: Int) -> Unit
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val statusBarHeight =
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        onInsetsApplied(statusBarHeight)
        insets
    }

    ViewCompat.requestApplyInsets(this)
}

/**
 * Adds the device's actual status-bar inset plus a small design spacing.
 * This is useful for content that is drawn edge-to-edge.
 */
fun View.applyStatusBarInset(extraTopDp: Int = 0) {
    val extraTopPx = (extraTopDp * resources.displayMetrics.density).toInt()

    setOnApplyStatusBarInsetsListener { statusBarHeight ->
        updatePadding(top = statusBarHeight + extraTopPx)
    }
}
