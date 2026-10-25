package com.akib.mycompanion.system

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.presentation.call.CallToken
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallTokenTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the token is made once and then stays the same`() {
        val first = CallToken.get(app)

        assertThat(CallToken.get(app)).isEqualTo(first)
        assertThat(app.getSharedPreferences("call_token", 0).getString("token", null)).isEqualTo(first)
    }

    @Test
    fun `the token is 32 random bytes, url-safe and on one line`() {
        val token = CallToken.get(app)

        // 32 bytes in base64 without wrapping: 44 characters, padding included.
        assertThat(token).hasLength(44)
        assertThat(token).matches("[A-Za-z0-9_=-]+")
    }

    @Test
    fun `only the exact token is valid`() {
        val token = CallToken.get(app)

        assertThat(CallToken.isValid(app, token)).isTrue()
        assertThat(CallToken.isValid(app, null)).isFalse()
        assertThat(CallToken.isValid(app, "")).isFalse()
        assertThat(CallToken.isValid(app, token.dropLast(1))).isFalse()
        assertThat(CallToken.isValid(app, token + "x")).isFalse()
    }

    @Test
    fun `a fresh install gets a different token`() {
        val first = CallToken.get(app)
        app.getSharedPreferences("call_token", 0).edit().clear().commit()

        assertThat(CallToken.get(app)).isNotEqualTo(first)
    }
}
