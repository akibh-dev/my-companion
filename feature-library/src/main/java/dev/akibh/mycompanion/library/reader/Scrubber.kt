package dev.akibh.mycompanion.library.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.abs

/**
 * How fast the scrubber follows the finger once it has moved [upDp] above the strip: full speed on
 * the strip, then half, a quarter and a tenth as the finger slides up, for landing on one exact page
 * in a long book (like a video scrubber's fine mode).
 */
internal fun scrubSpeed(upDp: Float): Float = when {
    upDp < 48f -> 1f
    upDp < 120f -> .5f
    upDp < 200f -> .25f
    else -> .1f
}

/** What the bubble says about [speed], or null at full speed. */
internal fun scrubSpeedLabel(speed: Float): String? = when (speed) {
    1f -> null
    .5f -> "Half speed"
    .25f -> "Quarter speed"
    else -> "Fine"
}

/**
 * A dotted strip through the book with the red dot at [value] (0..1). Press anywhere to grab it: the
 * strip grows ([expanded]) and a tap away from the dot jumps there first. While held, sideways moves
 * the dot, and sliding the finger up slows it ([scrubSpeed]). [onSpeed] reports the current speed.
 * TalkBack can set the position directly.
 */
@Composable
internal fun Scrubber(
    value: Float,
    expanded: Boolean,
    onGrab: () -> Unit,
    onChange: (value: Float, speed: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val empty = ink.copy(alpha = .22f)
    val dotRadius by animateDpAsState(if (expanded) 2.4.dp else 1.6.dp, label = "dot")
    val thumb by animateDpAsState(if (expanded) 13.dp else 8.dp, label = "thumb")
    val height by animateDpAsState(if (expanded) 56.dp else 48.dp, label = "height")
    val shown by animateFloatAsState(value, label = "value")
    val current by rememberUpdatedState(value)
    val grab by rememberUpdatedState(onGrab)
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    change(target.coerceIn(0f, 1f), 1f)
                    release()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val pad = THUMB_SLOP.toPx()
                    var at = current
                    // Pressing the dot grabs it where it is; pressing elsewhere on the strip jumps there.
                    if (abs(down.position.x - at * width) > pad) at = (down.position.x / width).coerceIn(0f, 1f)
                    grab()
                    change(at, 1f)
                    val stripY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        val up = (stripY - pointer.position.y).toDp().value
                        val speed = scrubSpeed(up)
                        at = (at + pointer.positionChange().x / width * speed).coerceIn(0f, 1f)
                        change(at, speed)
                        pointer.consume()
                    }
                    release()
                }
            },
    ) {
        val step = 6.dp.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        // Follows the finger exactly while held; glides after a jump (search, contents, Go to page).
        val drawn = if (expanded) value else shown
        val filled = (drawn.coerceIn(0f, 1f) * count).toInt()
        val y = size.height / 2
        repeat(count) { i ->
            drawCircle(
                if (i < filled) ink else empty,
                radius = dotRadius.toPx(),
                center = Offset(i * step + step / 2, y),
            )
        }
        drawCircle(CompanionRed, radius = thumb.toPx(), center = Offset(drawn * size.width, y))
    }
}

private val THUMB_SLOP = 24.dp
