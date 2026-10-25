package com.akib.mycompanion.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.akib.mycompanion.vault.data.Vault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Locks the vault a while after you leave it or the app (FR-2.3). */
@Singleton
class VaultAutoLock @Inject constructor(private val vault: Vault) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null

    fun keepOpen() {
        pending?.cancel()
    }

    fun lockSoon(afterMillis: Long = LOCK_AFTER_MILLIS) {
        pending?.cancel()
        pending = scope.launch {
            delay(afterMillis)
            vault.lock()
        }
    }

    companion object {
        const val LOCK_AFTER_MILLIS = 60_000L
    }
}

/**
 * While a vault screen is shown: no screenshots, a blank preview in Recents (FR-2.13), and the
 * auto-lock timer paused; leaving the screen or the app starts it.
 */
@Composable
fun SecureVaultScreen(autoLock: VaultAutoLock) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        autoLock.keepOpen()
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            autoLock.lockSoon()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { autoLock.lockSoon() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { autoLock.keepOpen() }
}

/** The system unlock prompt: fingerprint or face (strong), or the phone's PIN/pattern (FR-2.2). */
fun askToUnlock(context: Context, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val activity = context.findActivity() as? FragmentActivity ?: return onError("Can't show the unlock prompt here")
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode !in CANCELLED) {
                    onError(errString.toString())
                }
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Fingerprint, face or your screen lock")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build(),
    )
}

/** The user closed the prompt: not worth an error message. */
private val CANCELLED = setOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON)

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
