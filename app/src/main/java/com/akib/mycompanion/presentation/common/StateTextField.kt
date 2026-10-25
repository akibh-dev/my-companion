package com.akib.mycompanion.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation

/**
 * An outlined text field whose text lives in a [TextFieldState] (device finding #5).
 *
 * The old pattern sent every key press to a ViewModel StateFlow and drew the field from what came back.
 * That value can arrive a frame late, so fast typing right after focusing a field dropped or reordered
 * letters. Here the keyboard writes straight into Compose state; the ViewModel only reads it.
 *
 * Material3 in our Compose BOM (1.3.x) has no `OutlinedTextField(state = …)` yet, so this is
 * [BasicTextField] with the outlined decoration box (the same parts OutlinedTextField is built from).
 *
 * @param onImeAction run when the keyboard's action key (Done, Go…) is pressed; null keeps the default.
 * @param onUserEdit called when the user changes the text (typing, pasting), not when code sets it. Used
 *   to clear an error shown under the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedStateTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: (() -> Unit)? = null,
    onUserEdit: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    val currentOnUserEdit = rememberUpdatedState(onUserEdit)
    val currentOnImeAction = rememberUpdatedState(onImeAction)
    val keyboardActionHandler = remember {
        object : KeyboardActionHandler {
            override fun onKeyboardAction(performDefaultAction: () -> Unit) {
                val action = currentOnImeAction.value
                if (action != null) action() else performDefaultAction()
            }
        }
    }
    val inputTransformation = remember {
        object : InputTransformation {
            override fun TextFieldBuffer.transformInput() {
                if (originalText.toString() != asCharSequence().toString()) currentOnUserEdit.value?.invoke()
            }
        }
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    BasicTextField(
        state = state,
        modifier = modifier.defaultMinSize(
            minWidth = OutlinedTextFieldDefaults.MinWidth,
            minHeight = OutlinedTextFieldDefaults.MinHeight,
        ),
        enabled = enabled,
        inputTransformation = inputTransformation,
        textStyle = LocalTextStyle.current.copy(color = textColor),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = keyboardActionHandler,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines)
        },
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        decorator = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = state.text.toString(),
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                trailingIcon = trailingIcon,
                supportingText = supportingText?.let { { Text(it) } },
                colors = colors,
            )
        },
    )
}

private const val DISABLED_ALPHA = 0.38f
