package dev.akibh.mycompanion.library.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The book's dotted strip, and the page dial it opens (after the camera zoom wheel):
 *
 * - Tap the strip to jump; swipe along it to move through the whole book.
 * - Push the finger up, or press and hold: a half dial rises above the pill. Each tick is one page,
 *   a long numbered tick every ten. The page under the fixed red marker at the TOP of the dial is
 *   where you'll land, shown large with its chapter, so the finger (below, turning the dial) never
 *   covers it. Sideways turns the dial, the ticks following the finger like a wheel; slow is page
 *   by page, a flick accelerates. A light tick is felt per page. Let go to go there.
 */

/** Shared by the strip (which turns it) and the dial overlay (which draws it). */
@Stable
internal class PageDial {
    var open by mutableStateOf(false)

    /** The page index under the marker, fractional while turning. */
    var page by mutableFloatStateOf(0f)
}

/**
 * Pages per tick of finger movement on the dial, from the finger's speed in dp per millisecond:
 * one page per tick when slow, up to [MAX_ACCELERATION] when flicked.
 */
internal fun fineAcceleration(dpPerMs: Float): Float =
    (1f + (abs(dpPerMs) - SLOW_DP_PER_MS).coerceAtLeast(0f) * ACCELERATION_GAIN).coerceAtMost(MAX_ACCELERATION)

/** Where page [page] sits on the dial when [current] is under the marker: degrees from the top. */
internal fun tickOffsetDegrees(page: Int, current: Float): Float = (page - current) * DEGREES_PER_PAGE

/** Whether the finger has asked for the dial: pushed up far enough from where it pressed. */
internal fun pushedUp(upDp: Float): Boolean = upDp >= OPEN_PUSH_DP

/**
 * [value] is the place in the book (0..1) over [steps] = pages - 1. [expanded] while held.
 * TalkBack can set the position directly.
 */
@Composable
internal fun Scrubber(
    value: Float,
    steps: Int,
    expanded: Boolean,
    dial: PageDial,
    onChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val empty = ink.copy(alpha = .22f)
    val haptics = LocalHapticFeedback.current
    val grow by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "grow"
    )
    val shown by animateFloatAsState(value, label = "value")
    val current by rememberUpdatedState(value)
    val pages by rememberUpdatedState(steps)
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
                    val tick = DIAL_DRAG_PER_PAGE.toPx()
                    var at = current
                    // Pressing the dot grabs it where it is; pressing elsewhere on the strip jumps there.
                    if (abs(down.position.x - at * width) > THUMB_SLOP.toPx()) {
                        at = (down.position.x / width).coerceIn(0f, 1f)
                    }
                    change(at)
                    var page = at * pages
                    var lastTime = down.uptimeMillis
                    var moved = false

                    fun openDial() {
                        page = page.roundToInt().toFloat()
                        dial.page = page
                        dial.open = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }

                    while (true) {
                        // Holding still opens the dial too, so a timeout counts as a long press.
                        val event: PointerEvent? = if (!dial.open && !moved) {
                            withTimeoutOrNull(HOLD_MILLIS) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            openDial()
                            continue
                        }
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        val dx = pointer.positionChange().x
                        if (abs(dx) > 1f) moved = true
                        val ms = (pointer.uptimeMillis - lastTime).coerceAtLeast(1L)
                        lastTime = pointer.uptimeMillis
                        val up = (down.position.y - pointer.position.y).toDp().value
                        if (!dial.open && pushedUp(up)) openDial()
                        if (dial.open) {
                            // The ticks follow the finger, like turning a wheel: right goes back.
                            val before = page.roundToInt()
                            page -= dx / tick * fineAcceleration(dx.toDp().value / ms)
                            page = page.coerceIn(0f, pages.toFloat())
                            dial.page = page
                            if (page.roundToInt() != before) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        } else {
                            page = (page + dx / width * pages).coerceIn(0f, pages.toFloat())
                        }
                        at = if (pages > 0) page / pages else 0f
                        change(at)
                        pointer.consume()
                    }
                    if (dial.open) {
                        // Land exactly on the page under the marker.
                        page = page.roundToInt().toFloat()
                        change(if (pages > 0) page / pages else 0f)
                        dial.open = false
                    }
                    release()
                }
            },
    ) {
        val step = DOT_STEP.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val y = size.height / 2
        val drawn = if (expanded) value else shown
        val filled = (drawn.coerceIn(0f, 1f) * count).toInt()
        repeat(count) { i ->
            drawCircle(
                if (i < filled) ink else empty,
                radius = (1.6f + .8f * grow).dp.toPx(),
                center = Offset(i * step + step / 2, y),
            )
        }
        drawCircle(CompanionRed, radius = (8f + 5f * grow).dp.toPx(), center = Offset(drawn * size.width, y))
    }
}

/**
 * The half dial, drawn over the page while [dial] is open. Its lower edge sits [bottom] above the
 * screen's bottom (the top of the progress pill), so the pill stays visible under it. [titleAt]
 * names the chapter of a page index, or null.
 */
@Composable
internal fun PageDialOverlay(
    dial: PageDial,
    total: Int,
    unit: String,
    titleAt: (Int) -> String?,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val shown by animateFloatAsState(
        if (dial.open) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "dial",
    )
    if (shown <= .01f && !dial.open) return
    val colors = MaterialTheme.colorScheme
    val face = colors.surfaceContainerHigh
    val ink = colors.onSurface
    val faint = colors.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.labelMedium.copy(color = ink)
    val pageStyle = MaterialTheme.typography.headlineSmall.copy(color = ink, fontWeight = FontWeight.SemiBold)
    val chapterStyle = MaterialTheme.typography.labelSmall.copy(color = faint, letterSpacing = 1.4.sp)

    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // Solid well before it has finished rising, so the page never shows through.
                alpha = (shown * 2f).coerceIn(0f, 1f)
                // Rises from the pill.
                translationY = (1f - shown) * 40.dp.toPx()
            },
    ) {
        val radius = (size.width * .62f).coerceAtMost(DIAL_MAX_RADIUS.toPx())
        val lowerEdge = size.height - bottom.toPx()
        // The centre sits low so a wide arc (not a full half circle) shows above the pill.
        val centre = Offset(size.width / 2, lowerEdge - DIAL_VISIBLE.toPx() + radius)
        val top = centre.y - radius

        drawCircle(face, radius, centre)
        drawCircle(ink.copy(alpha = .08f), radius, centre, style = Stroke(1.dp.toPx()))

        val current = dial.page
        val span = (VISIBLE_DEGREES / DEGREES_PER_PAGE).toInt() + 1
        val first = (current.toInt() - span).coerceAtLeast(0)
        val last = (current.toInt() + span).coerceAtMost(total - 1)
        for (page in first..last) {
            val degrees = tickOffsetDegrees(page, current)
            if (abs(degrees) > VISIBLE_DEGREES) continue
            // Ticks fade towards the ends of the arc.
            val fade = 1f - (abs(degrees) / VISIBLE_DEGREES)
            val major = (page + 1) % 10 == 0 || page == 0
            val length = if (major) 18.dp.toPx() else 9.dp.toPx()
            val width = if (major) 2.dp.toPx() else 1.2.dp.toPx()
            tick(
                centre,
                radius,
                degrees,
                10.dp.toPx(),
                length,
                width,
                ink.copy(alpha = fade * if (major) .95f else .55f)
            )
            if (major) {
                val label = measurer.measure("${page + 1}", numberStyle.copy(color = ink.copy(alpha = fade)))
                val r = radius - 10.dp.toPx() - length - 14.dp.toPx()
                val rad = Math.toRadians((degrees - 90f).toDouble())
                val at = Offset(centre.x + (r * cos(rad)).toFloat(), centre.y + (r * sin(rad)).toFloat())
                drawText(label, topLeft = at - Offset(label.size.width / 2f, label.size.height / 2f))
            }
        }

        // The fixed marker: a red notch at the top of the dial.
        val marker = Path().apply {
            moveTo(centre.x - 7.dp.toPx(), top + 2.dp.toPx())
            lineTo(centre.x + 7.dp.toPx(), top + 2.dp.toPx())
            lineTo(centre.x, top + 13.dp.toPx())
            close()
        }
        drawPath(marker, CompanionRed)

        // The page under the marker, and its chapter, inside the dial below the numbers.
        val index = current.roundToInt().coerceIn(0, (total - 1).coerceAtLeast(0))
        val pageText = measurer.measure("$unit${index + 1}", pageStyle)
        val pageTop = top + 72.dp.toPx()
        drawText(pageText, topLeft = Offset(centre.x - pageText.size.width / 2f, pageTop))
        val chapter = titleAt(index)?.takeIf { it.isNotBlank() }?.uppercase()
        if (chapter != null) {
            val text = measurer.measure(
                chapter,
                chapterStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (radius * 1.1f).toInt()),
            )
            drawText(
                text,
                topLeft = Offset(centre.x - text.size.width / 2f, pageTop + pageText.size.height + 4.dp.toPx())
            )
        }
    }
}

/** One tick on the rim, [inset] in from it, [degrees] from the top. */
private fun DrawScope.tick(
    centre: Offset,
    radius: Float,
    degrees: Float,
    inset: Float,
    length: Float,
    width: Float,
    color: Color,
) {
    rotate(degrees, pivot = centre) {
        drawLine(
            color,
            start = Offset(centre.x, centre.y - radius + inset),
            end = Offset(centre.x, centre.y - radius + inset + length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private val DOT_STEP = 6.dp
private val THUMB_SLOP = 24.dp
private val DIAL_DRAG_PER_PAGE = 9.dp
private val DIAL_MAX_RADIUS = 360.dp
private val DIAL_VISIBLE = 190.dp
private const val DEGREES_PER_PAGE = 3f
private const val VISIBLE_DEGREES = 62f
private const val OPEN_PUSH_DP = 24f
private const val HOLD_MILLIS = 350L
private const val SLOW_DP_PER_MS = .15f
private const val ACCELERATION_GAIN = 6f
private const val MAX_ACCELERATION = 8f
