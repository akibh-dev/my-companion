package com.akib.mycompanion.debug

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Non-exported, debug-only screen. Running a lookup never calls any activity handoff method. */
@AndroidEntryPoint
class WhatsAppDiagnosticsActivity : ComponentActivity() {
    @Inject lateinit var diagnostics: WhatsAppCallDiagnostics
    private lateinit var contact: EditText
    private lateinit var output: TextView
    private var pendingContactId: Long? = null
    private var lookup: Job? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val id = pendingContactId
        pendingContactId = null
        if (id != null) inspect(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingContactId = savedInstanceState?.getLong(STATE_PENDING)?.takeIf { it > 0 }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        column.addView(
            TextView(this).apply {
                text = "WhatsApp lookup diagnostics\nNo call or chat will be started. Enter one saved app contact ID."
            }
        )
        contact = EditText(this).apply {
            id = View.generateViewId()
            hint = "Saved app contact ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(
                savedInstanceState?.getString(STATE_INPUT)
                    ?: intent.getLongExtra(EXTRA_CONTACT_ID, -1).takeIf { it > 0 }?.toString().orEmpty()
            )
        }
        column.addView(contact)
        column.addView(
            Button(this).apply {
                text = "Check contact (no call)"
                setOnClickListener { chosenId()?.let(::inspect) }
            }
        )
        column.addView(
            Button(this).apply {
                text = "Allow contacts for this lookup"
                setOnClickListener {
                    chosenId()?.let {
                        pendingContactId = it
                        permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }
        )
        output = TextView(this).apply {
            text = savedInstanceState?.getString(STATE_REPORT).orEmpty()
            setTextIsSelectable(true)
        }
        column.addView(output)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun chosenId(): Long? = contact.text.toString().toLongOrNull()?.takeIf { it > 0 }.also {
        if (it == null) output.text = "Enter a positive saved app contact ID."
    }

    private fun inspect(id: Long) {
        lookup?.cancel()
        lookup = lifecycleScope.launch { output.text = diagnostics.inspect(id).text() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_INPUT, contact.text.toString())
        outState.putString(STATE_REPORT, output.text.toString())
        pendingContactId?.let { outState.putLong(STATE_PENDING, it) }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_CONTACT_ID = "diagnostic_app_contact_id"
        private const val STATE_PENDING = "pending_diagnostic_contact"
        private const val STATE_INPUT = "diagnostic_contact_input"
        private const val STATE_REPORT = "sanitized_diagnostic_report"

        fun intent(context: Context, contactId: Long) = Intent(context, WhatsAppDiagnosticsActivity::class.java)
            .putExtra(EXTRA_CONTACT_ID, contactId)
    }
}
