package com.akib.mycompanion.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Opt-in design system. Does not replace the app's existing Material You theme. */
object CompanionColors {
    val Accent = Color(0xFFFF3B3B)
    val LightBackground = Color(0xFFF7F7F7)
    val DarkBackground = Color(0xFF0B0B0B)
}

private val light = lightColorScheme(
    primary = Color(0xFF101010),
    onPrimary = Color.White,
    background = CompanionColors.LightBackground,
    onBackground = Color(0xFF101010),
    surface = Color.White,
    onSurface = Color(0xFF101010),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFDEDEDE),
)
private val dark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF101010),
    background = CompanionColors.DarkBackground,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF353535),
)

@Composable
fun CompanionMinimalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) dark else light, content = content)
}

/** Original dotted-M motif. Give a label when the mark carries meaning. */
@Composable
fun CompanionDotMark(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val dots = listOf(
        0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
        1 to 1, 2 to 2, 3 to 3, 4 to 2, 5 to 1,
        6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4,
    )
    val semantics = if (label == null) Modifier else Modifier.semantics { contentDescription = label }
    Canvas(modifier.then(semantics).size(52.dp)) {
        val step = size.width / 8f
        dots.forEach { (x, y) ->
            drawCircle(ink, radius = step * 0.28f, center = Offset((x + 1) * step, (y + 1) * step))
        }
        drawCircle(CompanionColors.Accent, radius = step * 0.32f, center = Offset(7.2f * step, 6.4f * step))
    }
}

@Composable
fun CompanionSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun CompanionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier.then(clickModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CompanionStatusPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
