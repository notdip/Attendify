package com.dip.attendify.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────
// GlassCard
//
// A frosted/glossy card surface built WITHOUT real backdrop blur.
// minSdk 26 rules out RenderEffect-based blur (API 31+ only), and
// since this app has a flat near-black background (no imagery
// behind cards), real blur wouldn't read differently from a well
// -tuned overlay anyway. So the "glass" look here is two flat,
// UNIFORM layers (no positional gradient — same tint everywhere
// on the card, no light-to-dark falloff):
//
//   1. Base surface color (solid, from your existing token scale)
//   2. Uniform tint wash — flat `tintColor` at low alpha across
//      the whole card
//   3. Top-edge highlight — a flat, uniform-alpha line along the
//      top edge, same `tintColor`, simulating a beveled glass rim
//
// `tintColor` defaults to MaterialTheme.colorScheme.primary, so the
// glass tint follows the device's Material You / wallpaper-derived
// palette instead of a fixed white. Pass a different color if a
// specific card needs to opt out of the dynamic tint.
//
// Two variants:
//   Standard — full wash + brighter edge highlight.
//              Use for subject cards, session cards, task cards,
//              subject detail stats header, summary cards.
//   Light    — lighter wash + thinner edge highlight.
//              Use for timetable slot rows — dense lists shouldn't
//              compete visually with hero cards.
// ─────────────────────────────────────────────────────────────────

enum class GlassVariant {
    Standard,
    Light,
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Standard,
    shape: Shape = RoundedCornerShape(20.dp),
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tintColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val washAlpha = if (variant == GlassVariant.Standard) 0.10f else 0.05f
    val edgeAlpha = if (variant == GlassVariant.Standard) 0.30f else 0.18f
    val edgeWidth: Dp = if (variant == GlassVariant.Standard) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
            // Uniform tint wash — flat alpha, no gradient/falloff
            .background(tintColor.copy(alpha = washAlpha))
            .drawWithContent {
                drawContent()
                // Top-edge highlight — flat, uniform-alpha line the
                // full width of the card (no fade), simulating a
                // beveled glass rim without introducing a gradient.
                drawLine(
                    color       = tintColor.copy(alpha = edgeAlpha),
                    start       = Offset(0f, edgeWidth.toPx() / 2f),
                    end         = Offset(size.width, edgeWidth.toPx() / 2f),
                    strokeWidth = edgeWidth.toPx(),
                )
            },
    ) {
        content()
    }
}