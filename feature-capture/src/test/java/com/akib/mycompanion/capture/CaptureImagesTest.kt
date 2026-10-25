package com.akib.mycompanion.capture

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.capture.data.decodeCaptureImage
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureImagesTest {
    @get:Rule val folder = TemporaryFolder()

    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW)

    @Test
    fun `all eight EXIF orientations place pixels correctly without changing the file`() {
        val expected = listOf(
            listOf(0, 1, 2, 3, 4, 5),
            listOf(2, 1, 0, 5, 4, 3),
            listOf(5, 4, 3, 2, 1, 0),
            listOf(3, 4, 5, 0, 1, 2),
            listOf(0, 3, 1, 4, 2, 5),
            listOf(3, 0, 4, 1, 5, 2),
            listOf(5, 2, 4, 1, 3, 0),
            listOf(2, 5, 1, 4, 0, 3),
        )
        for (orientation in 1..8) {
            val file = image("photo-$orientation.png", 3, 2)
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            val original = file.readBytes()
            val decoded = requireNotNull(decodeCaptureImage(file))
            try {
                val swapsSides = orientation >= 5
                assertThat(decoded.width).isEqualTo(if (swapsSides) 2 else 3)
                assertThat(decoded.height).isEqualTo(if (swapsSides) 3 else 2)
                val pixels = IntArray(6)
                decoded.getPixels(pixels, 0, decoded.width, 0, 0, decoded.width, decoded.height)
                assertThat(pixels.toList()).containsExactlyElementsIn(expected[orientation - 1].map { colors[it] })
                    .inOrder()
                assertThat(file.readBytes()).isEqualTo(original)
            } finally {
                decoded.recycle()
            }
        }
    }

    @Test
    fun `a screenshot without EXIF keeps its dimensions and pixels`() {
        val decoded = requireNotNull(decodeCaptureImage(image("screenshot.png", 3, 2)))
        try {
            assertThat(decoded.width).isEqualTo(3)
            assertThat(decoded.height).isEqualTo(2)
            assertThat(decoded.getPixel(0, 0)).isEqualTo(Color.RED)
        } finally {
            decoded.recycle()
        }
    }

    @Test
    fun `large photos are sampled to at most 2048 pixels per side`() {
        val decoded = requireNotNull(decodeCaptureImage(image("large.png", 4097, 64)))
        try {
            assertThat(decoded.width).isAtMost(2048)
            assertThat(decoded.height).isGreaterThan(0)
        } finally {
            decoded.recycle()
        }
    }

    @Test
    fun `missing and corrupt files have no preview`() {
        assertThat(decodeCaptureImage(File(folder.root, "missing.jpg"))).isNull()
        val corrupt = folder.newFile("broken.jpg").apply { writeText("not an image") }
        assertThat(decodeCaptureImage(corrupt)).isNull()
    }

    private fun image(name: String, width: Int, height: Int): File {
        val file = folder.newFile(name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            if (width == 3 && height == 2) bitmap.setPixels(colors, 0, 3, 0, 0, 3, 2)
            file.outputStream().use { assertThat(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)).isTrue() }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
