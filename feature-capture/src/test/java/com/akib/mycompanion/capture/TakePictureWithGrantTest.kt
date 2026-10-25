package com.akib.mycompanion.capture

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.capture.ui.TakePictureWithGrant
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The camera intent grants the camera app access to our photo file explicitly (needed from Android 18). */
@RunWith(AndroidJUnit4::class)
class TakePictureWithGrantTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val photo = Uri.parse("content://com.akib.mycompanion.captures/photos/1.jpg")

    @Test
    fun `asks for a photo into our file`() {
        val intent = TakePictureWithGrant().createIntent(app, photo)

        assertThat(intent.action).isEqualTo(MediaStore.ACTION_IMAGE_CAPTURE)
        assertThat(intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)).isEqualTo(photo)
    }

    @Test
    fun `grants the camera app read and write access to that file explicitly`() {
        val intent = TakePictureWithGrant().createIntent(app, photo)

        assertThat(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isNotEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(intent.clipData!!.itemCount).isEqualTo(1)
        assertThat(intent.clipData!!.getItemAt(0).uri).isEqualTo(photo)
    }

    @Test
    fun `the result is read the same way as before`() {
        val contract = TakePictureWithGrant()

        assertThat(contract.parseResult(Activity.RESULT_OK, null)).isTrue()
        assertThat(contract.parseResult(Activity.RESULT_CANCELED, null)).isFalse()
    }
}
