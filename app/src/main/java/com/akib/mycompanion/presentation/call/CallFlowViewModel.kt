package com.akib.mycompanion.presentation.call

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallPreparation
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.presentation.contacts.CallMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CallFlowState {
    data object Loading : CallFlowState
    data class Permission(val permanent: Boolean, val requesting: Boolean = false) : CallFlowState
    data class Choose(val variants: List<WhatsAppVariant>) : CallFlowState
    data class Unavailable(val reason: WhatsAppCallFailure) : CallFlowState
    data class Ready(val variant: WhatsAppVariant?, val chatReason: WhatsAppCallFailure? = null) : CallFlowState
    data class Completed(val message: String) : CallFlowState
    data object HandedOff : CallFlowState
}

/** Saves identities and operation progress, never a provider data-row ID. */
@HiltViewModel
class CallFlowViewModel @Inject constructor(
    private val call: CallContactUseCase,
    private val saved: SavedStateHandle,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(restore())
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    fun start(contactId: Long) {
        if (!saved.contains(CONTACT)) saved[CONTACT] = contactId
        if (saved.get<String>(STAGE) in listOf(null, "RESOLVING")) resolve()
    }

    private fun resolve() {
        if (work?.isActive == true) return
        saved[STAGE] = "RESOLVING"
        mutableState.value = CallFlowState.Loading
        work = viewModelScope.launch {
            try {
                val preparation = withContext(io) { call.prepare(contactId()) }
                work = null
                when (preparation) {
                    is CallPreparation.Ready -> ready(preparation.variant)
                    CallPreparation.PermissionRequired -> permission(false, false)
                    is CallPreparation.ChooseVariant -> choose(preparation.variants)
                    is CallPreparation.Unavailable -> unavailable(preparation.reason)
                    CallPreparation.ContactNotFound -> completed("This person was removed from My Companion")
                    CallPreparation.AppMissing -> completed("WhatsApp isn't installed")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completed("Couldn't check the WhatsApp contact action. Try again from People.")
            }
        }
    }

    /** The visible activity calls this only while RESUMED, so handoffs stay in the foreground. */
    fun onForeground(granted: Boolean, permanent: Boolean) {
        val current = state.value
        if (current is CallFlowState.Permission) {
            if (granted) resolve() else if (!current.requesting) permission(permanent, false)
        } else if (current is CallFlowState.Ready) execute(current)
    }

    fun permissionRequested(): Boolean {
        val current = state.value as? CallFlowState.Permission ?: return false
        if (current.requesting || current.permanent) return false
        permission(false, true)
        return true
    }

    fun permissionReturned(granted: Boolean, permanent: Boolean) {
        if (state.value !is CallFlowState.Permission) return
        if (granted) resolve() else permission(permanent, false)
    }

    fun select(variant: WhatsAppVariant) {
        val current = state.value as? CallFlowState.Choose ?: return
        if (variant !in current.variants) return
        val reason = saved.get<String>(CHAT_REASON)?.let { enumValueOf<WhatsAppCallFailure>(it) }
        ready(variant, reason)
    }

    fun openChat() {
        val reason = when (val current = state.value) {
            is CallFlowState.Permission -> if (!current.requesting) WhatsAppCallFailure.PERMISSION_REQUIRED else return
            is CallFlowState.Unavailable -> current.reason
            else -> return
        }
        ready(null, reason)
    }

    private fun execute(request: CallFlowState.Ready) {
        // Consume before crossing the external activity boundary. Recreated processes never replay this handoff.
        saved[STAGE] = "CONSUMED"
        mutableState.value = CallFlowState.Loading
        work = viewModelScope.launch {
            try {
                val result = if (request.chatReason != null) {
                    call.openChat(contactId(), request.chatReason, request.variant)
                } else {
                    call(contactId(), requireNotNull(request.variant))
                }
                when (result) {
                    CallResult.CallStarted -> {
                        saved[STAGE] = "HANDED_OFF"
                        mutableState.value = CallFlowState.HandedOff
                    }
                    is CallResult.ChooseVariant -> choose(result.variants)
                    is CallResult.Failed -> {
                        if (result.reason == WhatsAppCallFailure.PERMISSION_REQUIRED) {
                            permission(false, false)
                        } else {
                            completed(CallMessages.forResult(result) ?: "WhatsApp couldn't open that action")
                        }
                    }
                    else -> completed(CallMessages.forResult(result) ?: "Handed over to WhatsApp")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                completed("Couldn't open WhatsApp. Try again from People.")
            }
        }
    }

    private fun contactId(): Long = checkNotNull(saved[CONTACT])

    private fun permission(permanent: Boolean, requesting: Boolean) {
        saved[STAGE] = if (requesting) "REQUESTING" else "PERMISSION"
        saved[PERMANENT] = permanent
        mutableState.value = CallFlowState.Permission(permanent, requesting)
    }

    private fun ready(variant: WhatsAppVariant?, reason: WhatsAppCallFailure? = null) {
        saved[STAGE] = "READY"
        saved[VARIANT] = variant?.name
        saved[CHAT_REASON] = reason?.name
        mutableState.value = CallFlowState.Ready(variant, reason)
    }

    private fun choose(variants: List<WhatsAppVariant>) {
        saved[STAGE] = "CHOICE"
        saved[VARIANTS] = ArrayList(variants.map { it.name })
        mutableState.value = CallFlowState.Choose(variants)
    }

    private fun unavailable(reason: WhatsAppCallFailure) {
        saved[STAGE] = "UNAVAILABLE"
        saved[CHAT_REASON] = reason.name
        mutableState.value = CallFlowState.Unavailable(reason)
    }

    private fun completed(message: String) {
        saved[STAGE] = "COMPLETED"
        saved[MESSAGE] = message
        mutableState.value = CallFlowState.Completed(message)
    }

    private fun restore(): CallFlowState = runCatching {
        when (saved.get<String>(STAGE)) {
            "PERMISSION", "REQUESTING" -> CallFlowState.Permission(
                saved[PERMANENT] ?: false,
                saved.get<String>(STAGE) == "REQUESTING"
            )
            "CHOICE" -> CallFlowState.Choose(
                saved.get<ArrayList<String>>(VARIANTS).orEmpty().map {
                    enumValueOf<WhatsAppVariant>(it)
                }
            )
            "UNAVAILABLE" -> CallFlowState.Unavailable(
                enumValueOf<WhatsAppCallFailure>(checkNotNull(saved[CHAT_REASON]))
            )
            "READY" -> CallFlowState.Ready(
                saved.get<String>(VARIANT)?.let { enumValueOf<WhatsAppVariant>(it) },
                saved.get<String>(CHAT_REASON)?.let { enumValueOf<WhatsAppCallFailure>(it) },
            )
            "CONSUMED", "HANDED_OFF", "COMPLETED" -> CallFlowState.Completed(
                saved[MESSAGE] ?: (
                    "The previous WhatsApp handoff will not be repeated. " +
                        "Start again from People if needed."
                    )
            )
            else -> CallFlowState.Loading
        }
    }.getOrElse {
        val message = "Couldn't restore the call choice. Close this screen and start again from People."
        saved[STAGE] = "COMPLETED"
        saved[MESSAGE] = message
        CallFlowState.Completed(message)
    }

    companion object {
        private const val CONTACT = "pending_contact"
        private const val STAGE = "call_stage"
        private const val PERMANENT = "permanent_denial"
        private const val VARIANT = "selected_variant"
        private const val VARIANTS = "offered_variants"
        private const val CHAT_REASON = "chat_reason"
        private const val MESSAGE = "call_message"
    }
}
