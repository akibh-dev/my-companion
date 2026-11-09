package com.akib.mycompanion.presentation

import androidx.lifecycle.SavedStateHandle
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.CallAccess
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppCallKind
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.port.WhatsAppLaunchResult
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.presentation.call.CallFlowState
import com.akib.mycompanion.presentation.call.CallFlowViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallFlowViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val person = Contact(5, "Synthetic", "+8801711123456")
    private val contacts = FakeContactRepository(listOf(person))
    private val gateway = Gateway()
    private val saved = SavedStateHandle()
    private fun flow(handle: SavedStateHandle = saved) = CallFlowViewModel(
        CallContactUseCase(contacts, gateway),
        handle,
        UnconfinedTestDispatcher()
    )
    private fun copy(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun `permission request survives recreation and is consumed exactly once after grant`() = runTest {
        val first = flow()
        first.start(person.id)
        assertThat(first.permissionRequested()).isTrue()
        val recreated = flow(copy(saved))
        recreated.start(999) // The originally saved ID wins over a recreated caller's stale argument.
        assertThat(recreated.state.value).isEqualTo(CallFlowState.Permission(false, true))
        assertThat(recreated.permissionRequested()).isFalse()
        assertThat(gateway.calls).isEmpty()
        gateway.lookup = found(WhatsAppVariant.STANDARD)
        recreated.permissionReturned(true, false)
        recreated.onForeground(true, false)
        recreated.onForeground(true, false)
        assertThat(gateway.calls.map { it.variant }).containsExactly(WhatsAppVariant.STANDARD)
    }

    @Test fun `saved consumed handoff is never automatically repeated by a recreated process`() = runTest {
        gateway.lookup = found(WhatsAppVariant.STANDARD)
        val first = flow()
        first.start(person.id)
        first.onForeground(true, false)
        val recreated = flow(copy(saved))
        recreated.start(person.id)
        recreated.onForeground(true, false)
        assertThat(gateway.calls).hasSize(1)
        assertThat(recreated.state.value).isInstanceOf(CallFlowState.Completed::class.java)
    }

    @Test fun `permanent denial keeps a usable explicit chat fallback and does not reprompt`() = runTest {
        val model = flow()
        model.start(person.id)
        model.permissionRequested()
        model.permissionReturned(false, true)
        model.onForeground(false, true)
        assertThat(model.state.value).isEqualTo(CallFlowState.Permission(true))
        assertThat(model.permissionRequested()).isFalse()
        model.openChat()
        model.onForeground(false, true)
        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.chats).containsExactly(WhatsAppVariant.STANDARD)
        assertThat((model.state.value as CallFlowState.Completed).message).contains("Contacts access")
    }

    @Test fun `both variants require a choice and the chosen app receives the current saved number`() = runTest {
        gateway.variants = WhatsAppVariant.entries
        gateway.lookup = found(*WhatsAppVariant.entries.toTypedArray())
        val model = flow()
        model.start(person.id)
        assertThat(model.state.value).isInstanceOf(CallFlowState.Choose::class.java)
        assertThat(gateway.calls).isEmpty()
        contacts.save(person.copy(phone = "+8801811123456"))
        model.select(WhatsAppVariant.BUSINESS)
        model.onForeground(true, false)
        assertThat(gateway.calls.map { it.variant }).containsExactly(WhatsAppVariant.BUSINESS)
        assertThat(gateway.lastNumber).isEqualTo("+8801811123456")
    }

    @Test fun `chat fallback choice never turns into a voice call or silently switches accounts`() = runTest {
        gateway.variants = WhatsAppVariant.entries
        val model = flow()
        model.start(person.id)
        model.openChat()
        model.onForeground(false, false)
        assertThat(model.state.value).isInstanceOf(CallFlowState.Choose::class.java)
        model.select(WhatsAppVariant.BUSINESS)
        model.onForeground(false, false)
        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.chats).containsExactly(WhatsAppVariant.BUSINESS)
    }

    @Test fun `granting permission in settings resumes the pending action once`() = runTest {
        val model = flow()
        model.start(person.id)
        model.permissionRequested()
        model.permissionReturned(false, true)
        gateway.lookup = found(WhatsAppVariant.STANDARD)
        model.onForeground(true, false)
        model.onForeground(true, false)
        assertThat(gateway.calls).hasSize(1)
    }

    @Test fun `deleted contact is explained without any handoff`() = runTest {
        val model = flow()
        model.start(404)
        assertThat((model.state.value as CallFlowState.Completed).message).contains("removed")
        assertThat(gateway.calls).isEmpty()
        assertThat(gateway.chats).isEmpty()
    }

    @Test fun `malformed restored choice fails visibly without replaying a handoff`() = runTest {
        val model = flow(SavedStateHandle(mapOf("call_stage" to "READY", "selected_variant" to "UNKNOWN")))
        model.start(person.id)
        model.onForeground(true, false)
        assertThat((model.state.value as CallFlowState.Completed).message).contains("Couldn't restore")
        assertThat(gateway.calls).isEmpty()
    }

    @Test fun `video kind survives permission recovery and activity recreation`() = runTest {
        val model = flow()
        model.start(person.id, WhatsAppCallKind.VIDEO)
        model.permissionRequested()
        val recreated = flow(copy(saved))
        recreated.start(person.id) // A default argument must not replace the saved video request.
        gateway.lookup = VoiceCallLookup.Found(listOf(VoiceCallTarget(50, "video.mime", kind = WhatsAppCallKind.VIDEO)))
        recreated.permissionReturned(true, false)
        recreated.onForeground(true, false)
        assertThat(gateway.calls.single().kind).isEqualTo(WhatsAppCallKind.VIDEO)
    }

    @Test fun `phone permission request survives recreation and resumes the same video call once`() = runTest {
        gateway.lookup = VoiceCallLookup.Found(listOf(VoiceCallTarget(51, "video.mime", kind = WhatsAppCallKind.VIDEO)))
        gateway.needsPhone = true
        val model = flow()
        model.start(person.id, WhatsAppCallKind.VIDEO)
        assertThat(model.state.value).isEqualTo(CallFlowState.Permission(false, access = CallAccess.PHONE))
        model.permissionRequested()
        val recreated = flow(copy(saved))
        recreated.start(person.id)
        assertThat((recreated.state.value as CallFlowState.Permission).access).isEqualTo(CallAccess.PHONE)
        gateway.needsPhone = false
        recreated.permissionReturned(true, false)
        recreated.onForeground(true, false)
        recreated.onForeground(true, false)
        assertThat(gateway.calls).hasSize(1)
        assertThat(gateway.calls.single().kind).isEqualTo(WhatsAppCallKind.VIDEO)
    }

    private fun found(vararg variants: WhatsAppVariant) = VoiceCallLookup.Found(
        variants.mapIndexed { index, variant ->
            VoiceCallTarget(index + 1L, "synthetic", variant)
        }
    )

    private class Gateway : WhatsAppGateway {
        var variants: List<WhatsAppVariant> = listOf(WhatsAppVariant.STANDARD)
        var lookup: VoiceCallLookup = VoiceCallLookup.Unavailable(WhatsAppCallFailure.PERMISSION_REQUIRED)
        var lastNumber = ""
        var needsPhone = false
        override fun needsCallPermission(target: VoiceCallTarget) = needsPhone
        val calls = mutableListOf<VoiceCallTarget>()
        val chats = mutableListOf<WhatsAppVariant>()
        override fun isInstalled() = variants.isNotEmpty()
        override fun installedVariants() = variants
        override fun findVoiceCallTarget(digits: String): VoiceCallTarget? = error("Use typed lookup")
        override fun startVoiceCall(target: VoiceCallTarget): Boolean = error("Use typed launch")
        override fun openChat(digits: String): Boolean = error("Use typed chat")
        override suspend fun resolveVoiceCall(digits: String): VoiceCallLookup {
            lastNumber = digits
            return lookup
        }
        override suspend fun resolveCall(
            digits: String,
            kind: WhatsAppCallKind
        ): VoiceCallLookup = resolveVoiceCall(digits)
        override suspend fun launchVoiceCall(target: VoiceCallTarget): WhatsAppLaunchResult {
            calls += target
            return WhatsAppLaunchResult.HandedOff
        }
        override fun launchChat(digits: String, variant: WhatsAppVariant): WhatsAppLaunchResult {
            chats += variant
            return WhatsAppLaunchResult.HandedOff
        }
    }
}
