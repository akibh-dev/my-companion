package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.fakes.FakeContactRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CallContactUseCaseTest {

    private val akib = Contact(id = 1, name = "Akib", phone = "+8801711123456")
    private val target = VoiceCallTarget(dataId = 42, mimeType = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call")
    private val whatsApp = mockk<WhatsAppGateway>(relaxed = true) {
        every { isInstalled() } returns true
    }
    private val useCase = CallContactUseCase(FakeContactRepository(listOf(akib)), whatsApp)

    @Test
    fun `starts the voice call when WhatsApp has an entry for the number`() {
        every { whatsApp.findVoiceCallTarget("8801711123456") } returns target
        every { whatsApp.startVoiceCall(target) } returns true

        assertThat(useCase(akib)).isEqualTo(CallResult.CallStarted)
        verify(exactly = 0) { whatsApp.openChat(any()) }
    }

    @Test
    fun `opens the chat when there is no call entry`() {
        every { whatsApp.findVoiceCallTarget(any()) } returns null
        every { whatsApp.openChat("8801711123456") } returns true

        assertThat(useCase(akib)).isEqualTo(CallResult.ChatOpened)
        verify(exactly = 0) { whatsApp.startVoiceCall(any()) }
    }

    @Test
    fun `falls back to the chat when the call cannot be started`() {
        every { whatsApp.findVoiceCallTarget(any()) } returns target
        every { whatsApp.startVoiceCall(target) } returns false
        every { whatsApp.openChat(any()) } returns true

        assertThat(useCase(akib)).isEqualTo(CallResult.ChatOpened)
    }

    @Test
    fun `reports WhatsApp missing when it is not installed`() {
        every { whatsApp.isInstalled() } returns false

        assertThat(useCase(akib)).isEqualTo(CallResult.WhatsAppNotInstalled)
        verify(exactly = 0) { whatsApp.findVoiceCallTarget(any()) }
        verify(exactly = 0) { whatsApp.openChat(any()) }
    }

    @Test
    fun `reports WhatsApp missing when nothing can open the chat`() {
        every { whatsApp.findVoiceCallTarget(any()) } returns null
        every { whatsApp.openChat(any()) } returns false

        assertThat(useCase(akib)).isEqualTo(CallResult.WhatsAppNotInstalled)
    }

    @Test
    fun `calling by id looks the person up first`() = runTest {
        every { whatsApp.findVoiceCallTarget(any()) } returns target
        every { whatsApp.startVoiceCall(any()) } returns true

        assertThat(useCase(1L)).isEqualTo(CallResult.CallStarted)
        assertThat(useCase(99L)).isEqualTo(CallResult.ContactNotFound)
    }
}
