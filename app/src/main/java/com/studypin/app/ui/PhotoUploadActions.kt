package com.studypin.app.ui

import android.content.Context

/** Shared photo-upload entry point until the real uploader is connected. */
fun Context.showPhotoUploadPlaceholder() {
    showMessage("Photo upload will be connected later")
}
