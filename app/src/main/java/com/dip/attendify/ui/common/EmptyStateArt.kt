package com.dip.attendify.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────
// C6 — Empty state illustrations
//
// Deliberately minimal: flat geometric primitives only (arcs,
// rounded rects, lines, circles), no custom vector art, no
// animation. Three states, matching the descoped plan:
//   1. WelcomeIllustration     — Home, first-run / no semester
//   2. NoSessionsIllustration  — Mark, no classes on selected day
//   3. NoTasksIllustration     — Tasks, empty list
// ─────────────────────────────────────────────────────────────────

@Composable
fun WelcomeIllustration(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(size)) {
        val stroke  = 8.dp.toPx()
        val inset   = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

        // Track ring
        drawArc(
            color      = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter  = false,
            topLeft    = Offset(inset, inset),
            size       = arcSize,
            style      = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Partial accent arc — representative, not tied to real data
        drawArc(
            color      = accentColor,
            startAngle = -90f,
            sweepAngle = 240f,
            useCenter  = false,
            topLeft    = Offset(inset, inset),
            size       = arcSize,
            style      = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Checkmark, centered
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val checkStroke = 6.dp.toPx()
        drawLine(
            color       = accentColor,
            start       = Offset(cx - 14.dp.toPx(), cy),
            end         = Offset(cx - 4.dp.toPx(), cy + 10.dp.toPx()),
            strokeWidth = checkStroke,
            cap         = StrokeCap.Round,
        )
        drawLine(
            color       = accentColor,
            start       = Offset(cx - 4.dp.toPx(), cy + 10.dp.toPx()),
            end         = Offset(cx + 16.dp.toPx(), cy - 12.dp.toPx()),
            strokeWidth = checkStroke,
            cap         = StrokeCap.Round,
        )
    }
}

@Composable
fun NoSessionsIllustration(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 88.dp,
) {
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val dashColor    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Canvas(modifier = modifier.size(size)) {
        val cardStroke = 3.dp.toPx()
        val corner     = CornerRadius(14.dp.toPx(), 14.dp.toPx())

        // Card outline (calendar day)
        drawRoundRect(
            color  = outlineColor,
            topLeft = Offset(cardStroke / 2f, cardStroke / 2f),
            size   = Size(this.size.width - cardStroke, this.size.height - cardStroke),
            cornerRadius = corner,
            style  = Stroke(width = cardStroke),
        )
        // Header bar
        drawLine(
            color       = outlineColor,
            start       = Offset(0f, this.size.height * 0.3f),
            end         = Offset(this.size.width, this.size.height * 0.3f),
            strokeWidth = cardStroke,
        )
        // Empty dashed row — "nothing scheduled"
        drawLine(
            color       = dashColor,
            start       = Offset(this.size.width * 0.22f, this.size.height * 0.62f),
            end         = Offset(this.size.width * 0.78f, this.size.height * 0.62f),
            strokeWidth = 5.dp.toPx(),
            cap         = StrokeCap.Round,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}

@Composable
fun NoTasksIllustration(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 88.dp,
) {
    val barColor   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val doneColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Canvas(modifier = modifier.size(size)) {
        val rowHeight = 10.dp.toPx()
        val rowGap    = 14.dp.toPx()
        val circleR   = 7.dp.toPx()
        val startX    = circleR * 2 + 10.dp.toPx()

        val rows = listOf(0.85f, 0.65f, 0.75f)
        rows.forEachIndexed { i, widthFrac ->
            val y = 14.dp.toPx() + i * (rowHeight + rowGap)

            // Checkbox circle — first row shown "done"
            if (i == 0) {
                drawCircle(
                    color  = doneColor,
                    radius = circleR,
                    center = Offset(circleR + 2.dp.toPx(), y + rowHeight / 2f),
                )
                val cx = circleR + 2.dp.toPx()
                val cy = y + rowHeight / 2f
                drawLine(
                    color       = Color.White,
                    start       = Offset(cx - 3.dp.toPx(), cy),
                    end         = Offset(cx - 0.5f.dp.toPx(), cy + 2.5f.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap         = StrokeCap.Round,
                )
                drawLine(
                    color       = Color.White,
                    start       = Offset(cx - 0.5f.dp.toPx(), cy + 2.5f.dp.toPx()),
                    end         = Offset(cx + 4.dp.toPx(), cy - 3.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap         = StrokeCap.Round,
                )
            } else {
                drawCircle(
                    color  = barColor,
                    radius = circleR,
                    center = Offset(circleR + 2.dp.toPx(), y + rowHeight / 2f),
                    style  = Stroke(width = 2.dp.toPx()),
                )
            }

            // Line bar
            drawRoundRect(
                color        = barColor,
                topLeft      = Offset(startX, y),
                size         = Size((this.size.width - startX) * widthFrac, rowHeight),
                cornerRadius = CornerRadius(rowHeight / 2f, rowHeight / 2f),
            )
        }
    }
}