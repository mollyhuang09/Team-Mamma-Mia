package com.studypin.app.ui

import android.content.Context
import android.widget.Toast

/** Shared photo-upload entry point until the real uploader is connected. */
fun Context.showPhotoUploadPlaceholder() {
    Toast.makeText(this, "Photo upload will be connected later", Toast.LENGTH_SHORT).show()
}
