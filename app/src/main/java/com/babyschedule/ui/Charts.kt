package com.babyschedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.babyschedule.util.formatNumber
import kotlin.math.ceil
import kotlin.math.max

private fun DrawScope.label(
    tm: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    alignX: Float = 0f,
    alignY: Float = 0f,
) {
    val layout = tm.measure(text, style)
    drawText(layout, topLeft = Offset(x - layout.size.width * alignX, y - layout.size.height * alignY))
}

/** Rounds a max value up to a "nice" number for the axis. */
private fun niceMax(v: Float): Float {
    if (v <= 0f) return 1f
    val steps = listOf(1f, 2f, 3f, 4f, 5f, 6f, 8f, 10f)
    var magnitude = 1f
    while (v / magnitude > 10f) magnitude *= 10f
    while (v / magnitude < 1f && magnitude > 0.01f) magnitude /= 10f
    val n = steps.first { it * magnitude >= v }
    return n * magnitude
}

/**
 * Vertical bar chart. [values] holds one list per bar, with one value per series
 * (stacked bottom to top in the order of [colors]).
 */
@Composable
fun StackedBarChart(
    labels: List<String>,
    values: List<List<Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    format: (Float) -> String = { formatNumber(it) },
) {
    val tm = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val totals = values.map { it.sum() }
        val maxV = niceMax(totals.maxOrNull() ?: 0f)
        val axisW = 36.dp.toPx()
        val bottomH = 20.dp.toPx()
        val topPad = 18.dp.toPx()
        val chartW = size.width - axisW
        val chartH = size.height - bottomH - topPad
        val baseY = topPad + chartH

        for (i in 0..3) {
            val y = baseY - chartH * i / 3f
            drawLine(grid, Offset(axisW, y), Offset(size.width, y), strokeWidth = 1f)
            label(tm, format(maxV * i / 3f), style, axisW - 6.dp.toPx(), y, alignX = 1f, alignY = 0.5f)
        }

        val n = values.size
        val slot = chartW / n
        val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val labelEvery = ceil(n / 8f).toInt().coerceAtLeast(1)
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        values.forEachIndexed { i, series ->
            val x = axisW + slot * i + (slot - barW) / 2f
            var top = baseY
            series.forEachIndexed { si, v ->
                if (v > 0f) {
                    val h = chartH * v / maxV
                    top -= h
                    drawRoundRect(colors[si % colors.size], Offset(x, top), Size(barW, h), radius)
                }
            }
            if (n <= 14 && totals[i] > 0f) {
                label(tm, format(totals[i]), style, x + barW / 2f, top - 2.dp.toPx(), alignX = 0.5f, alignY = 1f)
            }
            if ((n - 1 - i) % labelEvery == 0) {
                label(tm, labels[i], style, x + barW / 2f, baseY + 4.dp.toPx(), alignX = 0.5f)
            }
        }
    }
}

/** Simple line chart for values over time (e.g. weight). */
@Composable
fun TimeLineChart(
    points: List<Pair<Long, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    yFormat: (Float) -> String,
    xFormat: (Long) -> String,
    highlightFirst: Boolean = false,
) {
    val tm = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (points.isEmpty()) return@Canvas
        val axisW = 44.dp.toPx()
        val bottomH = 20.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val chartW = size.width - axisW - rightPad
        val chartH = size.height - bottomH - topPad

        var minX = points.minOf { it.first }
        var maxX = points.maxOf { it.first }
        if (maxX == minX) {
            minX -= 86_400_000L
            maxX += 86_400_000L
        }
        val rawMin = points.minOf { it.second }
        val rawMax = points.maxOf { it.second }
        val pad = max((rawMax - rawMin) * 0.15f, rawMax * 0.03f).coerceAtLeast(1f)
        val minY = rawMin - pad
        val maxY = rawMax + pad

        fun px(x: Long) = axisW + chartW * (x - minX).toFloat() / (maxX - minX).toFloat()
        fun py(y: Float) = topPad + chartH * (1f - (y - minY) / (maxY - minY))

        for (i in 0..3) {
            val v = minY + (maxY - minY) * i / 3f
            val y = py(v)
            drawLine(grid, Offset(axisW, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
            label(tm, yFormat(v), style, axisW - 6.dp.toPx(), y, alignX = 1f, alignY = 0.5f)
        }

        val xTicks = listOf(minX, (minX + maxX) / 2, maxX)
        xTicks.forEachIndexed { i, t ->
            val align = when (i) { 0 -> 0f; 2 -> 1f; else -> 0.5f }
            label(tm, xFormat(t), style, px(t), topPad + chartH + 4.dp.toPx(), alignX = align)
        }

        val sorted = points.sortedBy { it.first }
        if (sorted.size > 1) {
            val path = Path()
            sorted.forEachIndexed { i, (x, y) ->
                if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        sorted.forEachIndexed { i, (x, y) ->
            val c = Offset(px(x), py(y))
            drawCircle(color, radius = 5.dp.toPx(), center = c)
            drawCircle(surface, radius = 2.5.dp.toPx(), center = c)
            if (highlightFirst && i == 0) drawCircle(color, radius = 8.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        }
    }
}
