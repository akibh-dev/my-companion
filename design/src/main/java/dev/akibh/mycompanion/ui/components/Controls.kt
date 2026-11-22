package dev.akibh.mycompanion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akibh.mycompanion.ui.theme.CompanionRed

/*
 * The app's two kinds of pressable pill, kept visibly different:
 *
 * - A choice (ChoiceChip) is ON when it is a solid ink pill with a red signal dot, like a Nothing
 *   quick-settings tile, and OFF when it is a quiet grey pill. Neither state has a border, so a
 *   border can never be mistaken for "selected" (or the other way round).
 * - An action is a Button (solid ink: the main thing to do) or a SecondaryButton (taller grey pill
 *   with stronger text). Actions never carry the red dot.
 */

/**
 * One option in a set: tap to choose it. [multiple] marks options that can be on together.
 * [onLongClick] (with its TalkBack [onLongClickLabel]) adds a press-and-hold action, e.g. edit a shelf.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiple: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val role = if (multiple) Role.Checkbox else Role.RadioButton
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.onSurface else colors.surfaceContainerHigh,
        tween(CHOICE_MILLIS),
        label = "choice container",
    )
    val content by animateColorAsState(
        if (selected) colors.surface else colors.onSurfaceVariant,
        tween(CHOICE_MILLIS),
        label = "choice content",
    )
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(container)
            .then(
                if (onLongClick == null) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                } else {
                    Modifier
                        .semantics { this.selected = selected }
                        .combinedClickable(
                            enabled = enabled,
                            role = role,
                            onLongClickLabel = onLongClickLabel,
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                },
            )
            .alpha(if (enabled) 1f else .38f)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionRed).testTag(CHOICE_DOT_TAG))
        }
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Lays out a set of [ChoiceChip]s, wrapping onto new lines so no option is cut off. */
@Composable
fun ChoiceRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** A secondary action: a borderless grey pill. [destructive] colours the label for Delete and the like. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = if (destructive) colors.error else colors.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        content = content,
    )
}

const val CHOICE_DOT_TAG = "choice-selected-dot"
private const val CHOICE_MILLIS = 160
