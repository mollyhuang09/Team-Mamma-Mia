package com.studypin.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Spot photos are stored either as a remote URL or as an inline `data:` URI
 * (base64 JPEG) directly on the Firestore document. Coil loads a String URL
 * natively but not a data URI, so callers should pass photo strings through
 * [toLoadableModel] before handing them to Coil's `load(...)`.
 */
object ImageUtils {

    fun toLoadableModel(uri: String): Any {
        if (!uri.startsWith("data:")) return uri
        return decodeDataUri(uri) ?: uri
    }

    private fun decodeDataUri(uri: String): Bitmap? {
        val base64 = uri.substringAfter(',', missingDelimiterValue = "")
        if (base64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
