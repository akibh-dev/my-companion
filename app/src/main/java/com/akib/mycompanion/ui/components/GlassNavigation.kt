package com.akib.mycompanion.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akib.mycompanion.Tab

/** A real blurred copy of content beneath the bar, with a neutral tint and subtle highlight. */
@Composable
fun GlassNavigation(
    selected: Tab?,
    compact: Boolean,
    backdrop: GraphicsLayer,
    contentOrigin: Offset,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (compact) 272.dp else 328.dp, tween(180), label = "Navigation width")
    val height by animateDpAsState(if (compact) 64.dp else 76.dp, tween(180), label = "Navigation height")
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.onSurface.luminance() > .5f
    val tint = if (dark) Color(0xFF343A3D) else Color(0xFFDAD9D6)
    val selectedIndex by animateFloatAsState(
        targetValue = (selected?.ordinal ?: 0).toFloat(),
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "Selected tab position",
    )
    val selectionColor = if (dark) Color(0xFF242424).copy(alpha = .94f) else Color.White.copy(alpha = .98f)
    val foreground = if (dark) Color.White else Color(0xFF181818)
    val shape = RoundedCornerShape(40.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        BoxWithConstraints(
            Modifier.align(Alignment.Center).width(width).height(height).testTag("companion-navigation")
                .onGloballyPositioned { origin = it.positionInRoot() }
                .clip(shape)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = .32f), Color.White.copy(alpha = .05f))),
                    shape
                ),
        ) {
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    renderEffect = BlurEffect(20f, 20f, TileMode.Clamp)
                },
            ) {
                val offset = contentOrigin - origin
                translate(offset.x, offset.y) { drawLayer(backdrop) }
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(tint.copy(alpha = .72f), tint.copy(alpha = .58f)))
                )
            )
            val slotWidth = (maxWidth - 12.dp - 9.dp) / Tab.entries.size
            Box(
                Modifier.offset {
                    IntOffset((6.dp + (slotWidth + 3.dp) * selectedIndex).roundToPx(), 6.dp.roundToPx())
                }.width(slotWidth).height(height - 12.dp).clip(RoundedCornerShape(30.dp))
                    .background(selectionColor).testTag("navigation-selection"),
            )
            Row(
                Modifier.matchParentSize().selectableGroup().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Tab.entries.forEach { tab ->
                    val active = tab == selected
                    Column(
                        Modifier.weight(1f).height(height - 12.dp).clip(RoundedCornerShape(30.dp))
                            .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                            .semantics { contentDescription = tab.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(tab.icon),
                            null,
                            tint = foreground,
                            modifier = Modifier.size(24.dp),
                        )
                        if (!compact) {
                            Text(
                                tab.label,
                                color = foreground,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
