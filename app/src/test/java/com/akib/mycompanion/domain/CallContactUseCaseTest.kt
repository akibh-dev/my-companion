package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallPreparation
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakeWhatsAppGateway
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CallContactUseCaseTest {
    private val contact = Contact(1, "Example", "+8801711123456")
    private val target = VoiceCallTarget(42, "voice.mime")
    private val gateway = FakeWhatsAppGateway(target = target)
    private val contacts = FakeContactRepository(listOf(contact))
    private val useCase = CallContactUseCase(contacts, gateway)

    @Test fun `prepare is noncalling and invocation reports handoff without claiming connected call`() = runTest {
        assertThat(useCase.prepare(1)).isEqualTo(CallPreparation.Ready(WhatsAppVariant.STANDARD))
        assertThat(gateway.calls).isEmpty()
        assertThat(useCase(1L)).isEqualTo(CallResult.CallStarted)
        assertThat(gateway.lookups).hasSize(2)
        assertThat(gateway.chats).isEmpty()
    }

    @Test fun `missing contact and absent package are distinct and avoid lookup launch`() = runTest {
        assertThat(useCase(99L)).isEqualTo(CallResult.ContactNotFound)
        gateway.installed = false
        assertThat(useCase(1L)).isEqualTo(CallResult.WhatsAppNotInstalled)
        assertThat(gateway.lookups).isEmpty()
        assertThat(gateway.calls).isEmpty()
    }

    @Test fun `permission recovery preparation never opens a chat`() = runTest {
        gateway.lookup = VoiceCallLookup.Unavailable(WhatsAppCallFailure.PERMISSION_REQUIRED)
        assertThat(useCase.prepare(1)).isEqualTo(CallPreparation.PermissionRequired)
        assertThat(gateway.chats).isEmpty()
        assertThat(useCase.openChat(1, WhatsAppCallFailure.PERMISSION_REQUIRED))
            .isEqualTo(CallResult.ChatFallback(WhatsAppCallFailure.PERMISSION_REQUIRED, WhatsAppVariant.STANDARD))
        assertThat(gateway.chats).containsExactly("8801711123456")
    }

    @Test fun `missing voice row preserves truthful chat fallback reason`() = runTest {
        gateway.lookup = VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_PHONEBOOK_MATCH)
        assertThat(useCase(1L))
            .isEqualTo(CallResult.ChatFallback(WhatsAppCallFailure.NO_PHONEBOOK_MATCH, WhatsAppVariant.STANDARD))
    }

    @Test fun `failed chat launch is not reported as WhatsApp absent`() = runTest {
        gateway.lookup = VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW)
        gateway.chatWorks = false
        gateway.chatFailure = WhatsAppCallFailure.LAUNCH_DENIED
        assertThat(useCase(1L)).isEqualTo(CallResult.Failed(WhatsAppCallFailure.LAUNCH_DENIED))
    }

    @Test fun `both targets require explicit choice and selected attempt re-resolves`() = runTest {
        gateway.variants = WhatsAppVariant.entries
        val business = target.copy(dataId = 43, variant = WhatsAppVariant.BUSINESS)
        gateway.lookup = VoiceCallLookup.Found(listOf(target, business))
        assertThat(useCase.prepare(1)).isEqualTo(CallPreparation.ChooseVariant(WhatsAppVariant.entries))
        assertThat(useCase(1L)).isEqualTo(CallResult.ChooseVariant(WhatsAppVariant.entries))
        assertThat(gateway.calls).isEmpty()
        gateway.lookup = VoiceCallLookup.Found(listOf(target, business.copy(dataId = 88)))
        assertThat(useCase(1, WhatsAppVariant.BUSINESS)).isEqualTo(CallResult.CallStarted)
        assertThat(gateway.calls.single().dataId).isEqualTo(88)
        assertThat(gateway.calls.single().variant).isEqualTo(WhatsAppVariant.BUSINESS)
    }

    @Test fun `a selected app rejected call falls back only to that same app`() = runTest {
        gateway.variants = WhatsAppVariant.entries
        gateway.lookup = VoiceCallLookup.Found(listOf(target.copy(variant = WhatsAppVariant.BUSINESS)))
        gateway.callWorks = false
        gateway.callFailure = WhatsAppCallFailure.STALE_TARGET
        assertThat(useCase(1, WhatsAppVariant.BUSINESS))
            .isEqualTo(CallResult.ChatFallback(WhatsAppCallFailure.STALE_TARGET, WhatsAppVariant.BUSINESS))
        assertThat(gateway.chatVariants).containsExactly(WhatsAppVariant.BUSINESS)
    }

    @Test fun `ambiguous targets never launch a call and app choice is still explicit for chat`() = runTest {
        gateway.variants = WhatsAppVariant.entries
        gateway.lookup = VoiceCallLookup.Unavailable(WhatsAppCallFailure.AMBIGUOUS)
        assertThat(useCase(1L)).isEqualTo(CallResult.ChooseVariant(WhatsAppVariant.entries))
        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.chats).isEmpty()
    }
}
