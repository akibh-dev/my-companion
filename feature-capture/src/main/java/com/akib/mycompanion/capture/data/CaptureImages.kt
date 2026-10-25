package com.akib.mycompanion.capture.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

/** Decode a preview off the main thread, leaving the original camera file and its metadata intact. */
internal fun decodeCaptureImage(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    // Screenshots may have no EXIF. Missing or unreadable metadata should still allow a preview.
    val exif = try {
        ExifInterface(file)
    } catch (_: IOException) {
        return bitmap
    }
    val matrix = Matrix().apply {
        // EXIF defines a horizontal flip before the clockwise rotation (including transpose/transverse).
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    if (matrix.isIdentity) return bitmap
    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented !== bitmap) bitmap.recycle()
    return oriented
}

private const val MAX_IMAGE_SIDE = 2048
