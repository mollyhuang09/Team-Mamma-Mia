package com.studypin.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Shared pipeline for user-picked photos: corrects EXIF orientation, then blurs any
 * detected faces for privacy before the bitmap is shown or uploaded. Used by both the
 * "add spot" and "add review" photo pickers so the privacy behavior stays consistent.
 */
object ImageCaptureHelper {

    fun processPickedImage(
        context: Context,
        uri: Uri,
        onProcessed: (Bitmap, facesBlurred: Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val bitmap = try {
            @Suppress("DEPRECATION")
            correctOrientation(context, MediaStore.Images.Media.getBitmap(context.contentResolver, uri), uri)
        } catch (e: Exception) {
            onFailure(e)
            return
        }

        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )

        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onProcessed(bitmap, false)
                } else {
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    faces.forEach { face ->
                        val box = face.boundingBox
                        val dynamicBlockSize = (minOf(box.width(), box.height()) / 12).coerceAtLeast(8)
                        pixelateRegion(mutableBitmap, box, dynamicBlockSize)
                    }
                    onProcessed(mutableBitmap, true)
                }
            }
            .addOnFailureListener {
                onProcessed(bitmap, false)
            }
    }

    private fun correctOrientation(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        val degrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun pixelateRegion(bitmap: Bitmap, region: Rect, blockSize: Int) {
        val left = region.left.coerceIn(0, bitmap.width)
        val top = region.top.coerceIn(0, bitmap.height)
        val right = region.right.coerceIn(0, bitmap.width)
        val bottom = region.bottom.coerceIn(0, bitmap.height)

        var blockY = top
        while (blockY < bottom) {
            val blockHeight = minOf(blockSize, bottom - blockY)
            var blockX = left
            while (blockX < right) {
                val blockWidth = minOf(blockSize, right - blockX)

                var redSum = 0L
                var greenSum = 0L
                var blueSum = 0L
                var pixelCount = 0
                for (y in blockY until blockY + blockHeight) {
                    for (x in blockX until blockX + blockWidth) {
                        val pixel = bitmap.getPixel(x, y)
                        redSum += (pixel shr 16) and 0xFF
                        greenSum += (pixel shr 8) and 0xFF
                        blueSum += pixel and 0xFF
                        pixelCount++
                    }
                }

                if (pixelCount > 0) {
                    val avgRed = (redSum / pixelCount).toInt()
                    val avgGreen = (greenSum / pixelCount).toInt()
                    val avgBlue = (blueSum / pixelCount).toInt()
                    val avgColor = (0xFF shl 24) or (avgRed shl 16) or (avgGreen shl 8) or avgBlue

                    for (y in blockY until (blockY + blockHeight)) {
                        for (x in blockX until (blockX + blockWidth)) {
                            bitmap.setPixel(x, y, avgColor)
                        }
                    }
                }

                blockX += blockSize
            }
            blockY += blockSize
        }
    }
}
