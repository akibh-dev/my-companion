package com.akib.mycompanion

import android.graphics.Bitmap
import android.media.AudioManager
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.debug.WhatsAppDiagnosticEntryPoint
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in only: this fixture can ring the user-selected real recipient. */
@RunWith(AndroidJUnit4::class)
class WhatsAppLiveCallDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val report = StringBuilder()

    @Test fun chosenRecipientViaAppButtonsAndEnhancedUi() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Real calls require explicit opt-in", arguments.getString("allow_live_calls") == "true")
        val wanted = WhatsAppNumberMatcher.normalize(arguments.getString("live_call_number"))
        assumeTrue("Explicit recipient required", wanted != null)
        val audio = context.getSystemService(AudioManager::class.java)
        assumeTrue("Do not interrupt an existing call", audio.mode == AudioManager.MODE_NORMAL)
        val contacts = EntryPointAccessors.fromApplication(context, WhatsAppDiagnosticEntryPoint::class.java)
            .diagnosticContacts().observeAll().first()
        val person = requireNotNull(contacts.firstOrNull { WhatsAppNumberMatcher.normalize(it.phone) == wanted })
        try {
            ActivityScenario.launch<MainActivity>(MainActivity.openTabIntent(context, Tab.PEOPLE)).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                screenshot("people")
                for (kind in listOf("voice", "video")) {
                    val label = if (kind == "voice") {
                        "Call ${person.name} on WhatsApp"
                    } else {
                        "Video call ${person.name} on WhatsApp"
                    }
                    click(waitNode { it.contentDescription?.toString() == label })
                    val deadline = SystemClock.elapsedRealtime() + 20_000
                    var outgoing: AccessibilityNodeInfo? = null
                    while (SystemClock.elapsedRealtime() < deadline && outgoing == null) {
                        findNode { it.text?.toString() == "Allow phone calls" }?.let(::click)
                        findNode { it.viewIdResourceName?.endsWith(":id/permission_allow_button") == true }?.let(
                            ::click
                        )
                        outgoing = findNode { it.viewIdResourceName == "com.whatsapp:id/end_call_button" }
                        if (outgoing == null) SystemClock.sleep(120)
                    }
                    assertTrue("$kind did not reach WhatsApp's outgoing call screen", outgoing != null)
                    report.appendLine("$kind=outgoing_call_screen_from_app_button")
                    click(requireNotNull(outgoing))
                    SystemClock.sleep(500)
                    context.startActivity(MainActivity.openTabIntent(context, Tab.PEOPLE))
                    waitNode { it.contentDescription?.toString() == "Reminders" }
                }
                click(waitNode { it.contentDescription?.toString() == "Reminders" })
                val create = waitNode { it.text?.toString() == "Create a reminder" }
                screenshot("reminders-list")
                click(create)
                waitNode { it.text?.toString() == "New reminder" }
                SystemClock.sleep(300)
                screenshot("reminder-editor")
                click(waitNode { it.contentDescription?.toString() == "Close reminder editor" })
                click(waitNode { it.contentDescription?.toString() == "Home" })
                automation.executeShellCommand("input swipe 540 500 540 1900 300").close()
                SystemClock.sleep(400)
                screenshot("home-expanded")
                automation.executeShellCommand("input swipe 540 1800 540 500 300").close()
                SystemClock.sleep(400)
                screenshot("home-compact")
                report.appendLine("enhanced_ui=captured")
                scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
        } finally {
            File(context.filesDir, "enhanced-live-call-report.txt").writeText(report.toString())
        }
    }

    private fun click(node: AccessibilityNodeInfo) {
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertTrue("Control did not accept click", clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(180)
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            repeat(node.childCount) { index ->
                node.getChild(index)?.let { visit(it)?.let { found -> return found } }
            }
            return null
        }
        return automation.rootInActiveWindow?.let(::visit)
    }

    private fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            findNode(predicate)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected UI control not available; check that the phone is unlocked")
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(200)
        if (automation.rootInActiveWindow?.packageName != context.packageName) return
        automation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "enhanced-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
