package com.hakim.liquify.catalog.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hakim.liquify.GlassLook
import com.hakim.liquify.GlassMaterial
import com.hakim.liquify.GlassRim
import com.hakim.liquify.LiquifyTheme
import com.hakim.liquify.catalog.ui.CircularCapsule
import com.hakim.liquify.catalog.ui.LocalCatalogColors
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule

/**
 * A segmented control for switching between views within a screen — distinct from
 * [LiquidBottomTabs]: no drag, just tap-to-select with a sliding accent pill. The bar's full drag
 * physics would be overkill for a secondary, in-screen control.
 *
 * The bar is chrome glass ([barMaterial] in [barTint]); the sliding pill is the selection animation,
 * which every look keeps.
 *
 * @param segmentRims a rim for each segment, drawn around it whether it is selected or not — for a
 *   bar that picks a rim, so every choice shows itself before it is made. `null` draws none.
 */
@Composable
fun LiquidTopTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmentRims: List<GlassRim?>? = null,
) {
    val colors = LocalCatalogColors.current
    val accent = colors.accent

    BoxWithConstraints(
        modifier
            .height(40f.dp)
            .liquify(Capsule(), tint = barTint(), material = barMaterial())
            .padding(3f.dp),
    ) {
        val tabWidth = maxWidth / titles.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f),
            label = "topTabIndicator",
        )

        Box(
            Modifier
                // Read in the placement phase: the slide re-places the pill instead of recomposing
                // the bar and its labels on every frame.
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .fillMaxHeight()
                .width(tabWidth)
                .background(accent, CircularCapsule)
        )

        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { index, title ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircularCapsule)
                        .semantics { selected = index == selectedIndex }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    segmentRims?.getOrNull(index)?.let { rim -> SegmentRim(rim) }
                    BasicText(
                        title,
                        style = TextStyle(
                            color = if (index == selectedIndex) Color.White else colors.content,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * [rim] around one segment, and nothing else. Solid glass samples nothing, so this is the rim drawn
 * over a plain shape — no layer recorded, no backdrop, no shader beyond the rim's own.
 */
@Composable
private fun BoxScope.SegmentRim(rim: GlassRim) {
    val material = remember(rim) { GlassMaterial(tint = Color.Transparent, rim = rim, shadow = null) }
    LiquifyTheme(look = GlassLook.Solid) {
        Box(Modifier.matchParentSize().liquify(Capsule(), material = material))
    }
}
