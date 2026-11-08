package com.akib.mycompanion.ui.components

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

enum class DotSymbol(val path: String) {
    VOICE("M4 16V12A8 8 0 0 1 20 12V16 M4 12H7V20H3V12H4 M20 12H17V20H21V12H20"),
    RULES("M12 3V8 M5 21V16H19V21 M5 16V12H19V16 M12 8V12"),
    REPLY("M8 5L2 11L8 17 M2 11H13A7 7 0 0 1 20 18V20"),
    SCREEN("M9 2H15Q18 2 18 5V19Q18 22 15 22H9Q6 22 6 19V5Q6 2 9 2 M10 18H14"),
    BELL("M18 8A6 6 0 0 0 6 8C6 15 3 15 3 17H21C21 15 18 15 18 8 M10 21H14"),
    TOOLS("M5 4V5 M12 4V5 M19 4V5 M5 11V12 M12 11V12 M19 11V12 M5 18V19 M12 18V19 M19 18V19"),
}

@Composable
fun DotIcon(symbol: DotSymbol, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val points = remember(symbol) {
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
    Canvas(modifier) {
        val scale = min(size.width, size.height) / 24f
        points.forEach { point -> drawCircle(color, scale * .7f, point * scale) }
    }
}

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
