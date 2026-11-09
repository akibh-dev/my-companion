package com.akib.mycompanion

import android.Manifest
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.debug.DiagnosticLookupStatus
import com.akib.mycompanion.debug.WhatsAppCallDiagnostics
import com.akib.mycompanion.debug.WhatsAppDiagnosticEntryPoint
import com.akib.mycompanion.debug.WhatsAppDiagnosticsActivity
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import com.akib.mycompanion.presentation.call.CallFlowActivity
import com.akib.mycompanion.presentation.call.CallFlowState
import com.akib.mycompanion.presentation.call.CallFlowViewModel
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** These fixtures never click a production Call button or invoke a gateway launch API. */
@RunWith(AndroidJUnit4::class)
class WhatsAppDiagnosticDeviceTest {
    @Test fun diagnosticsHostIsPrivateAndPreservesChosenIdAcrossRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, WhatsAppDiagnosticsActivity::class.java),
            PackageManager.ComponentInfoFlags.of(0),
        )
        assertThat(activity.exported).isFalse()
        ActivityScenario.launch<WhatsAppDiagnosticsActivity>(WhatsAppDiagnosticsActivity.intent(context, 99014)).use {
            onView(isAssignableFrom(EditText::class.java)).check(matches(withText("99014")))
            it.recreate()
            onView(isAssignableFrom(EditText::class.java)).check(matches(withText("99014")))
            onView(withText("Check contact (no call)")).check(matches(withText("Check contact (no call)")))
        }
    }

    @Test fun deniedDiagnosticIsSanitizedAndCannotStartAnActivity() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var launches = 0
        val denied = object : ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.READ_CONTACTS) {
                    PackageManager.PERMISSION_DENIED
                } else {
                    super.checkPermission(permission, pid, uid)
                }

            override fun startActivity(intent: Intent) {
                launches++
                throw AssertionError("A lookup diagnostic must never hand off an activity")
            }
        }
        val synthetic = Contact(99015, "private_person_canary", "+8801711123456")
        val repository = object : ContactRepository {
            override fun observeAll() = flowOf(listOf(synthetic))
            override suspend fun get(id: Long): Contact? = synthetic.takeIf { it.id == id }
            override suspend fun findByPhone(phone: String): Contact? = null
            override suspend fun save(contact: Contact): Contact = error("Read-only diagnostic fixture")
            override suspend fun delete(id: Long) = error("Read-only diagnostic fixture")
        }
        val report = WhatsAppCallDiagnostics(denied, repository, AndroidWhatsAppGateway(denied)).inspect(synthetic.id)
        assertThat(report.lookup.status).isEqualTo(DiagnosticLookupStatus.PERMISSION_MISSING)
        assertThat(report.text()).doesNotContain(synthetic.name)
        assertThat(report.text()).doesNotContain(synthetic.phone)
        assertThat(report.text()).doesNotContain(synthetic.digits)
        assertThat(report.text()).contains("handoff=not_attempted")
        assertThat(launches).isEqualTo(0)
    }

    @Test fun productionRecoveryHostRecreatesForRemovedContactWithoutCalling() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(context, WhatsAppDiagnosticEntryPoint::class.java)
        val removedContactId = Long.MAX_VALUE
        assumeTrue("Fixture needs an unused app contact ID", entry.diagnosticContacts().get(removedContactId) == null)
        ActivityScenario.launch<CallFlowActivity>(CallFlowActivity.intent(context, removedContactId)).use { scenario ->
            var model: CallFlowViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[CallFlowViewModel::class.java] }
            val before = withTimeout(10_000) { requireNotNull(model).state.first { it is CallFlowState.Completed } }
            assertThat((before as CallFlowState.Completed).message).contains("removed")
            scenario.recreate()
            scenario.onActivity {
                val restored = ViewModelProvider(it)[CallFlowViewModel::class.java].state.value
                assertThat(restored).isEqualTo(before)
            }
        }
    }

    /** Explicit opt-in: -e diagnostic_contact_id APP_CONTACT_ID. No permission is granted automatically. */
    @Test fun inspectChosenContactWithoutLaunchingAnything() = runBlocking<Unit> {
        val id = InstrumentationRegistry.getArguments().getString("diagnostic_contact_id")?.toLongOrNull()
        assumeTrue("Choose one saved app contact explicitly with diagnostic_contact_id", id != null && id > 0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnostic = EntryPointAccessors.fromApplication(context, WhatsAppDiagnosticEntryPoint::class.java)
            .whatsAppDiagnostics()
        val report = diagnostic.inspect(requireNotNull(id)).text()
        assertThat(report).contains("handoff=not_attempted")
        assertThat(report).contains("call_outcome=not_observed")
        File(context.filesDir, "whatsapp-call-diagnostics.txt").writeText(report)
    }

    /** Read-only diagnosis for a user-specified number; never logs contact identity. */
    @Test fun inspectChosenNumberWithoutLaunchingAnything() = runBlocking<Unit> {
        val number = InstrumentationRegistry.getArguments().getString("diagnostic_phone")
        assumeTrue("Choose a test phone explicitly", number != null)
        val normalized = WhatsAppNumberMatcher.normalize(number)
        requireNotNull(normalized)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(context, WhatsAppDiagnosticEntryPoint::class.java)
        val chosen = entry.diagnosticContacts().observeAll().first().firstOrNull {
            WhatsAppNumberMatcher.normalize(it.phone) == normalized
        }
        val output = if (chosen == null) {
            val gateway = AndroidWhatsAppGateway(context)
            buildString {
                for (kind in com.akib.mycompanion.domain.port.WhatsAppCallKind.entries) {
                    val check = gateway.inspect("+$normalized", kind)
                    appendLine("kind=$kind")
                    appendLine(
                        "lookup_status=" + when (val result = check.result) {
                            is com.akib.mycompanion.domain.port.VoiceCallLookup.Found -> "READY"
                            is com.akib.mycompanion.domain.port.VoiceCallLookup.Unavailable -> result.reason.name
                        }
                    )
                    appendLine("phonebook_matches=${check.phonebookMatches}")
                    appendLine("candidate_rows=${check.candidateRows}")
                    appendLine("matched_rows=${check.observations.count { it.numberMatches }}")
                    appendLine("handler_available=${check.observations.any { it.handlerAvailable }}")
                }
                appendLine("handoff=not_attempted")
            }
        } else {
            entry.whatsAppDiagnostics().inspect(chosen.id).text()
        }
        File(context.filesDir, "whatsapp-call-diagnostics.txt").writeText(output)
    }
}
