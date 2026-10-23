package com.akib.mycompanion.presentation.reminders

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import kotlinx.coroutines.launch
import java.util.Calendar

/** Connects [RemindersScreen] to its ViewModel and to Android (notification permission, time picker). */
@Composable
fun RemindersRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RemindersEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text) }
            }
        }
    }

    // Re-checked on every resume: the user may have just changed it in system settings.
    var notificationsAllowed by remember { mutableStateOf(AndroidReminderNotifier.canPost(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = AndroidReminderNotifier.canPost(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var submitAfterPermission by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = AndroidReminderNotifier.canPost(context)
        if (submitAfterPermission) {
            submitAfterPermission = false
            viewModel.onSubmit()
        } else if (!granted && !shouldShowRationale(context)) {
            // "Don't ask again" was chosen earlier, so no dialog appeared: settings is the only way.
            openNotificationSettings(context)
        }
    }

    RemindersScreen(
        state = state,
        notificationsAllowed = notificationsAllowed,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        onTitleChange = viewModel::onTitleChange,
        onMessageChange = viewModel::onMessageChange,
        onChoiceChange = viewModel::onChoiceChange,
        onPickTime = {
            val current = state.form.choice as? WhenChoice.At
            val now = Calendar.getInstance()
            TimePickerDialog(
                context,
                { _, hour, minute -> viewModel.onChoiceChange(WhenChoice.At(hour, minute)) },
                current?.hour ?: now.get(Calendar.HOUR_OF_DAY),
                current?.minute ?: now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context),
            ).show()
        },
        onRepeatDailyChange = viewModel::onRepeatDailyChange,
        onContactChange = viewModel::onContactChange,
        onSubmit = {
            if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                submitAfterPermission = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onSubmit()
            }
        },
        onCancelReminder = viewModel::onCancel,
        modifier = modifier,
    )
}

private fun shouldShowRationale(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val activity = context.findActivity() ?: return false
    return activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
