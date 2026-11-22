package dev.akibh.mycompanion.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.fileSize
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.theme.CompanionRed
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The approved card: a quiet rounded surface. */
val CardShape = RoundedCornerShape(26.dp)
val CoverShape = RoundedCornerShape(10.dp)

/**
 * A number set with − and +: one grey pill like a SecondaryButton, each button a 48 dp target
 * carrying its own TalkBack label.
 */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = STEPPER_TINT)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", minusLabel, minusEnabled, onMinus)
        Text(
            value,
            Modifier.widthIn(min = 56.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        StepButton("+", plusLabel, plusEnabled, onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else .38f),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The same grey as a SecondaryButton: a tint of the text colour, so it shows on any surface. */
private const val STEPPER_TINT = .10f

/** A thin progress line drawn as dots, filled up to [progress]. */
@Composable
fun DotProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    empty: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Canvas(modifier.height(4.dp).fillMaxWidth()) {
        val step = 6.dp.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val filled = (progress.coerceIn(0f, 1f) * count).toInt()
        repeat(count) { i ->
            drawCircle(
                if (i < filled) color else empty,
                radius = 1.6.dp.toPx(),
                center = Offset(i * step + step / 2, size.height / 2),
            )
        }
    }
}

/** A ring of dots, filled clockwise up to [progress]; the ring turns red once the goal is met. */
@Composable
fun DotRing(progress: Float, modifier: Modifier = Modifier, dots: Int = 48) {
    val full = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val radius = size.minDimension / 2 - 4.dp.toPx()
        val filled = (progress.coerceIn(0f, 1f) * dots).toInt()
        repeat(dots) { i ->
            val angle = -PI / 2 + 2 * PI * i / dots
            drawCircle(
                color = when {
                    progress >= 1f -> CompanionRed
                    i < filled -> full
                    else -> empty
                },
                radius = 2.6.dp.toPx(),
                center = Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat()),
            )
        }
    }
}

/** A dot-matrix bar chart: one column of dots per value, the last (today) in red. */
@Composable
fun DotBars(values: List<Long>, modifier: Modifier = Modifier, rows: Int = 8) {
    val full = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val stepX = size.width / values.size
        val stepY = size.height / rows
        val radius = minOf(stepX, stepY) * .3f
        values.forEachIndexed { column, value ->
            val lit = if (value <= 0) 0 else ((value.toDouble() / max) * rows).toInt().coerceIn(1, rows)
            repeat(rows) { row ->
                val on = row < lit
                drawCircle(
                    color = when {
                        on && column == values.lastIndex -> CompanionRed
                        on -> full
                        else -> empty
                    },
                    radius = radius,
                    center = Offset(column * stepX + stepX / 2, size.height - (row * stepY + stepY / 2)),
                )
            }
        }
    }
}

/** A book's cover, or a quiet dotted placeholder with its title. */
@Composable
fun Cover(book: Book, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(CoverShape)
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val cover = book.coverPath?.let(::File)?.takeIf { it.exists() }
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DotIcon(DotSymbol.BOOK, Modifier.size(28.dp), color = colors.onSurfaceVariant)
                Text(
                    book.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.format.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small badge on a cover: "Missing" in red, or the format. */
@Composable
fun CoverBadge(text: String, alert: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (alert) CompanionRed else Color.Black.copy(alpha = .72f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

/** A round dotted icon button for the reader's and library's bars: a 48dp touch target. */
@Composable
fun DotButton(
    symbol: DotSymbol,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        DotIcon(symbol, Modifier.size(22.dp), color = if (active) CompanionRed else MaterialTheme.colorScheme.onSurface)
    }
}

/** What the missing-file dialog and screen say. [availability] null: the file is there but unreadable. */
data class MissingText(val title: String, val body: String)

fun missingText(book: Book, availability: Availability?): MissingText {
    val file = "\"${book.fileName}\" (${book.size.fileSize()})"
    val kept = "Your place, bookmarks and reading time are kept."
    return when (availability) {
        Availability.MISSING -> MissingText(
            "File missing",
            "$file isn't where it was. It was deleted, or moved to another folder. " +
                "Locate it to link it again. $kept",
        )
        Availability.NO_ACCESS -> MissingText(
            "Can't reach the file",
            "The app can't read $file any more. It was deleted or moved, or access was lost " +
                "(after restoring a backup). Locate it to link it again. $kept",
        )
        Availability.OK, null -> MissingText(
            "Can't open",
            "$file is there, but it couldn't be opened. It may be damaged, not fully downloaded, " +
                "or locked with DRM. You can link another copy. $kept",
        )
    }
}
