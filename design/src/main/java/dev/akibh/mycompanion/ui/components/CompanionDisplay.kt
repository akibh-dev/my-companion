package dev.akibh.mycompanion.ui.components

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import kotlin.math.min
import kotlin.math.roundToInt

/** Display lettering only. TalkBack and UI tests receive normal text, never individual dots. */
@Composable
fun DotText(
    value: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    color: Color = LocalContentColor.current,
) {
    if (value.any { it != '\n' && it !in dotAlphabet }) {
        Text(value, modifier, color = color, fontSize = fontSize)
        return
    }
    val lines = remember(value) { value.lines() }
    val columns = remember(value) {
        lines.maxOf { line -> line.sumOf { dotAlphabet.getValue(it).substringBefore('/').length + 1 } }
            .coerceAtLeast(1)
    }
    val lineHeight = with(LocalDensity.current) { fontSize.toDp() }
    Canvas(
        modifier
            .width(lineHeight * (columns / 7f))
            .height(lineHeight * (lines.size + (lines.size - 1) * .25f))
            .semantics {
                text = AnnotatedString(value.replace('\n', ' '))
                heading()
            },
    ) {
        val step = min(size.width / columns, size.height / (lines.size * 7f + (lines.size - 1) * 1.75f))
        lines.forEachIndexed { lineIndex, line ->
            var x = 0
            line.forEach { char ->
                val glyph = dotAlphabet.getValue(char).split('/')
                glyph.forEachIndexed { row, pattern ->
                    pattern.forEachIndexed { column, bit ->
                        if (bit == '1') {
                            drawCircle(
                                color,
                                step * .32f,
                                Offset((x + column + .5f) * step, (lineIndex * 8.75f + row + .5f) * step)
                            )
                        }
                    }
                }
                x += glyph.first().length + 1
            }
        }
    }
}

/** [filled] symbols are drawn as a solid dot-matrix (dots on a grid inside the shape), the rest as dotted outlines. */
enum class DotSymbol(val path: String, val filled: Boolean = false) {
    VOICE("M4 16V12A8 8 0 0 1 20 12V16 M4 12H7V20H3V12H4 M20 12H17V20H21V12H20"),
    RULES("M12 3V8 M5 21V16H19V21 M5 16V12H19V16 M12 8V12"),
    REPLY("M8 5L2 11L8 17 M2 11H13A7 7 0 0 1 20 18V20"),
    SCREEN("M9 2H15Q18 2 18 5V19Q18 22 15 22H9Q6 22 6 19V5Q6 2 9 2 M10 18H14"),
    BELL("M18 8A6 6 0 0 0 6 8C6 15 3 15 3 17H21C21 15 18 15 18 8 M10 21H14"),
    TOOLS("M5 4V5 M12 4V5 M19 4V5 M5 11V12 M12 11V12 M19 11V12 M5 18V19 M12 18V19 M19 18V19"),
    PLAY("M6.5 3.5L20 12L6.5 20.5Z", filled = true),
    PAUSE("M6 4H10.4V20H6Z M13.6 4H18V20H13.6Z", filled = true),
    LOCK("M7 11V8A5 5 0 0 1 17 8V11 M5 11H19V21H5V11"),
    MESSAGE("M4 4H20V16H11L6 20V16H4V4"),
    SPARK("M12 3Q13 11 21 12Q13 13 12 21Q11 13 3 12Q11 11 12 3"),
    NEXT("M3.5 4.5L15 12L3.5 19.5Z M16.6 4.5H20V19.5H16.6Z", filled = true),
    PREVIOUS("M20.5 4.5L9 12L20.5 19.5Z M4 4.5H7.4V19.5H4Z", filled = true),
    NOTE("M9 18V5L20 3V16 M9 18A3 3 0 1 1 6 15A3 3 0 0 1 9 18 M20 16A3 3 0 1 1 17 13A3 3 0 0 1 20 16"),
    PLUS("M12 4V20 M4 12H20"),

    /** An open book: two pages meeting at the spine. */
    BOOK("M12 6Q8 3 3 4V19Q8 18 12 21Q16 18 21 19V4Q16 3 12 6 M12 6V21"),

    /** Books standing on a shelf, one leaning: the library. */
    SHELF("M3 4H7V20H3Z M9 6H13V20H9Z M15 5.5L18.6 4.6L21.6 19.2L18 20.1Z M2 22H22"),

    /** A magnifying glass. */
    SEARCH("M10.5 3.5A7 7 0 1 1 10.5 17.5A7 7 0 1 1 10.5 3.5 M15.5 15.5L21 21"),

    /** A ribbon bookmark. */
    BOOKMARK("M6 3H18V21L12 16L6 21Z"),

    /** A filled ribbon: the page is bookmarked. */
    BOOKMARKED("M6 3H18V21L12 16L6 21Z", filled = true),

    /** Lines with bullets: contents. */
    CONTENTS("M3 6H5 M8 6H21 M3 12H5 M8 12H21 M3 18H5 M8 18H21"),

    /** A big and a small letter: text size and look. */
    TYPE("M2 20L8 4L14 20 M4.5 14H11.5 M15 11H22 M18.5 11V20"),

    /** Rising bars: reading stats. */
    CHART("M4 20V13 M10 20V8 M16 20V11 M22 20V4"),

    /** An eight-tooth cog with a hollow centre (the hole winds the other way, so it stays empty). */
    GEAR(
        "M9.78 4.73L9.98 1.59L14.02 1.59L14.22 4.73L15.57 5.29L17.93 3.21L20.79 6.07L18.71 8.43L19.27 9.78" +
            "L22.41 9.98L22.41 14.02L19.27 14.22L18.71 15.57L20.79 17.93L17.93 20.79L15.57 18.71L14.22 19.27" +
            "L14.02 22.41L9.98 22.41L9.78 19.27L8.43 18.71L6.07 20.79L3.21 17.93L5.29 15.57L4.73 14.22" +
            "L1.59 14.02L1.59 9.98L4.73 9.78L5.29 8.43L3.21 6.07L6.07 3.21L8.43 5.29Z " +
            "M15.60 12.00L15.42 10.89L14.91 9.88L14.12 9.09L13.11 8.58L12.00 8.40L10.89 8.58L9.88 9.09" +
            "L9.09 9.88L8.58 10.89L8.40 12.00L8.58 13.11L9.09 14.12L9.88 14.91L10.89 15.42L12.00 15.60" +
            "L13.11 15.42L14.12 14.91L14.91 14.12L15.42 13.11Z",
        filled = true,
    ),
}

@Composable
fun DotIcon(symbol: DotSymbol, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    // Computed once per symbol for the whole app: parsing and sampling a path on every appearance
    // added to the first frame of each screen.
    val points = dotPoints(symbol)
    Canvas(modifier) {
        val scale = min(size.width, size.height) / 24f
        // Filled glyphs use fatter dots so they read as a solid dot-matrix shape.
        val radius = scale * if (symbol.filled) .8f else .7f
        points.forEach { point -> drawCircle(color, radius, point * scale) }
    }
}

private val dotPointCache = java.util.EnumMap<DotSymbol, List<Offset>>(DotSymbol::class.java)

/** The dots that draw [symbol] in a 24-unit box: along its outline, or filling it for [DotSymbol.filled]. */
private fun dotPoints(symbol: DotSymbol): List<Offset> = dotPointCache.getOrPut(symbol) {
    if (symbol.filled) return@getOrPut filledDots(symbol.path)
    val measure = PathMeasure(PathParser.createPathFromPathData(symbol.path), false)
    buildList {
        do {
            val steps = (measure.length / 2.2f).roundToInt().coerceAtLeast(1)
            val point = FloatArray(2)
            repeat(steps) { index ->
                measure.getPosTan(index * measure.length / steps, point, null)
                add(Offset(point[0], point[1]))
            }
        } while (measure.nextContour())
    }
}

/** Grid points (every [FILL_STEP] units of the 24-unit box) that fall inside [pathData]. */
private fun filledDots(pathData: String): List<Offset> {
    // Region works in whole pixels, so test on a 100x finer copy of the 24-unit box to keep edges crisp.
    val scaled = PathParser.createPathFromPathData(pathData).apply {
        transform(android.graphics.Matrix().apply { setScale(100f, 100f) })
    }
    val fine = android.graphics.Region().apply {
        setPath(scaled, android.graphics.Region(0, 0, FILL_GRID * 100, FILL_GRID * 100))
    }
    return buildList {
        var y = FILL_STEP / 2
        while (y < 24f) {
            var x = FILL_STEP / 2
            while (x < 24f) {
                if (fine.contains((x * 100).roundToInt(), (y * 100).roundToInt())) add(Offset(x, y))
                x += FILL_STEP
            }
            y += FILL_STEP
        }
    }
}

private const val FILL_STEP = 2.0f
private const val FILL_GRID = 24

@Composable
fun DotPattern(modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = .22f)) {
    Canvas(modifier) {
        val step = 17.dp.toPx()
        for (x in 0..(size.width / step).toInt()) {
            for (y in 0..(size.height / step).toInt()) {
                drawCircle(color, 1.dp.toPx(), Offset(x * step + step / 2, y * step + step / 2))
            }
        }
    }
}
