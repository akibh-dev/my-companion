package dev.akibh.mycompanion.library.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.abs
import kotlin.math.exp

/*
 * The book's dotted strip, with two ways to move:
 *
 * - Sideways on the strip: the red dot follows the finger across the whole book (fast, coarse).
 * - Push the finger up: the dots around it rise into a bell curve (a fisheye, like a dock), and
 *   each dot is now ONE page. Sideways then steps page by page; moving quickly accelerates, so a
 *   flick still covers ground. Drop back onto the strip to go coarse again.
 */

/** How much the curve has risen (0..1) once the finger is [upDp] above where it pressed. */
internal fun bellLift(upDp: Float): Float = ((upDp - LIFT_START_DP) / LIFT_RANGE_DP).coerceIn(0f, 1f)

/** The bell's height at [distance] from its centre, 1 at the centre and fading out with [sigma]. */
internal fun bell(distance: Float, sigma: Float): Float = exp(-(distance * distance) / (2 * sigma * sigma))

/**
 * Pages per dot of finger movement on the raised curve, from the finger's speed in dp per
 * millisecond: one page per dot when slow, up to [MAX_ACCELERATION] when flicked.
 */
internal fun fineAcceleration(dpPerMs: Float): Float =
    (1f + (abs(dpPerMs) - SLOW_DP_PER_MS).coerceAtLeast(0f) * ACCELERATION_GAIN).coerceAtMost(MAX_ACCELERATION)

/**
 * [value] is the place in the book (0..1) over [steps] pages-1. [expanded] while held. [label] is
 * drawn at the top of the red dot while held (e.g. "p. 186 / 357"). TalkBack can set the position.
 */
@Composable
internal fun Scrubber(
    value: Float,
    steps: Int,
    expanded: Boolean,
    label: String,
    onGrab: () -> Unit,
    onChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val empty = ink.copy(alpha = .22f)
    val tipColor = MaterialTheme.colorScheme.inverseSurface
    val tipText = MaterialTheme.colorScheme.inverseOnSurface
    val measurer = rememberTextMeasurer()
    val tipStyle = MaterialTheme.typography.labelLarge.copy(color = tipText, fontWeight = FontWeight.Medium)

    // Where the finger is along the strip (px) and how far the curve has risen (0..1).
    var fingerX by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var liftTarget by remember { mutableFloatStateOf(0f) }
    val lift by animateFloatAsState(
        if (expanded) liftTarget else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "lift",
    )
    val grow by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "grow"
    )
    val shown by animateFloatAsState(value, label = "value")
    val current by rememberUpdatedState(value)
    val pages by rememberUpdatedState(steps)
    val grab by rememberUpdatedState(onGrab)
    val change by rememberUpdatedState(onChange)
    val release by rememberUpdatedState(onRelease)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    change(target.coerceIn(0f, 1f))
                    release()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val dotStep = DOT_STEP.toPx()
                    var at = current
                    // Pressing the dot grabs it where it is; pressing elsewhere on the strip jumps there.
                    if (abs(down.position.x - at * width) > THUMB_SLOP.toPx()) {
                        at = (down.position.x / width).coerceIn(0f, 1f)
                    }
                    fingerX = down.position.x
                    fingerY = down.position.y
                    liftTarget = 0f
                    grab()
                    change(at)
                    var page = at * pages
                    var lastTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        val dx = pointer.positionChange().x
                        val ms = (pointer.uptimeMillis - lastTime).coerceAtLeast(1L)
                        lastTime = pointer.uptimeMillis
                        val up = (down.position.y - pointer.position.y).toDp().value
                        liftTarget = bellLift(up)
                        fingerX = pointer.position.x.coerceIn(0f, width)
                        fingerY = pointer.position.y
                        if (liftTarget > 0f) {
                            // Raised: one dot of movement is one page, faster when flicked.
                            val speed = dx.toDp().value / ms
                            page += dx / dotStep * fineAcceleration(speed)
                        } else {
                            // On the strip: the whole book across its width.
                            page += dx / width * pages
                        }
                        page = page.coerceIn(0f, pages.toFloat())
                        at = if (pages > 0) page / pages else 0f
                        change(at)
                        pointer.consume()
                    }
                    liftTarget = 0f
                    release()
                }
            },
    ) {
        val step = DOT_STEP.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val y = size.height / 2
        val drawn = if (expanded) value else shown
        val filled = (drawn.coerceIn(0f, 1f) * count).toInt()
        // While held the curve sits under the finger; otherwise under the dot.
        val centre = if (expanded) fingerX else drawn * size.width
        // The peak rides just above the fingertip, so the finger never covers the dots or the page;
        // the higher the push, the taller and wider the curve and the bigger the dots near its top.
        val reach = (y - fingerY + ABOVE_FINGER.toPx()).coerceIn(BELL_MIN.toPx(), BELL_MAX.toPx())
        val height = reach * lift
        val tall = (height / BELL_MAX.toPx()).coerceIn(0f, 1f)
        val sigma = BELL_SIGMA.toPx() + height * .1f
        fun rise(x: Float) = height * bell(x - centre, sigma)

        // A faint guide along the curve, like the rail behind the dots.
        if (lift > .02f) {
            val guide = Path()
            var x = 0f
            guide.moveTo(0f, y - rise(0f))
            while (x <= size.width) {
                guide.lineTo(x, y - rise(x))
                x += 4.dp.toPx()
            }
            drawPath(guide, ink.copy(alpha = .12f * lift), style = Stroke(1.dp.toPx()))
        }

        repeat(count) { i ->
            val x = i * step + step / 2
            val near = bell(x - centre, sigma * 1.3f)
            // Fisheye: dots near the finger grow; more when the curve is up.
            val radius = (1.6f + .8f * grow + (2f + 2.4f * tall) * lift * near).dp.toPx()
            drawCircle(if (i < filled) ink else empty, radius = radius, center = Offset(x, y - rise(x)))
        }

        val thumbX = if (expanded && lift > .02f) centre else drawn * size.width
        val thumbY = y - rise(thumbX)
        val thumbRadius = (8f + 5f * grow + (2f + 2f * tall) * lift).dp.toPx()
        drawCircle(CompanionRed, radius = thumbRadius, center = Offset(thumbX, thumbY))

        // The page under the finger, over the red dot, while held.
        if (grow > .5f && label.isNotBlank()) {
            val text = measurer.measure(label, tipStyle)
            val padX = 12.dp.toPx()
            val padY = 6.dp.toPx()
            val w = text.size.width + padX * 2
            val h = text.size.height + padY * 2
            val left = (thumbX - w / 2).coerceIn(-step, size.width - w + step)
            val top = thumbY - thumbRadius - 10.dp.toPx() - h
            drawRoundRect(tipColor, Offset(left, top), Size(w, h), CornerRadius(h / 2))
            drawText(text, topLeft = Offset(left + padX, top + padY))
        }
    }
}

private val DOT_STEP = 6.dp
private val THUMB_SLOP = 24.dp
private val BELL_SIGMA = 26.dp
private val BELL_MIN = 40.dp
private val BELL_MAX = 220.dp
private val ABOVE_FINGER = 48.dp
private const val LIFT_START_DP = 16f
private const val LIFT_RANGE_DP = 64f
private const val SLOW_DP_PER_MS = .15f
private const val ACCELERATION_GAIN = 6f
private const val MAX_ACCELERATION = 8f
