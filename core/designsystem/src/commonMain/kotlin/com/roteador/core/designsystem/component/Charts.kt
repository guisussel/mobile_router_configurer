package com.roteador.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One line series for [LineChart]. */
data class ChartSeries(val values: List<Double>, val color: Color)

/** A labeled horizontal line on the Y axis, e.g. "-60 dBm". [color] = null uses the default label color. */
data class ChartTick(val value: Double, val label: String, val color: Color? = null)

/**
 * Minimal live line chart: newest value at the right edge, shared Y scale from
 * [minValue] to the max of all series (or [maxValue] if given). Each tick is a
 * grid line with its value written over it, so the reading is visible on the chart.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    capacity: Int,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    minValue: Double = 0.0,
    maxValue: Double? = null,
    /** Smallest top of the Y axis when auto-scaling, so near-zero noise stays flat. */
    minimumTop: Double = minValue + 1.0,
    /** Grid lines for a given top of the axis; default = halves of the range, unlabeled. */
    ticks: (top: Double) -> List<ChartTick> = { top -> listOf(ChartTick((minValue + top) / 2, "")) },
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val top = maxValue ?: maxOf(series.flatMap { it.values }.maxOrNull() ?: minValue, minimumTop)
            val range = (top - minValue).takeIf { it > 0 } ?: 1.0
            fun y(value: Double) = size.height - ((value - minValue) / range).coerceIn(0.0, 1.0).toFloat() * size.height

            ticks(top).forEach { tick ->
                val ty = y(tick.value)
                drawLine(tick.color?.copy(alpha = 0.5f) ?: grid, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 1f)
                if (tick.label.isNotEmpty()) {
                    val text = measurer.measure(tick.label, labelStyle.copy(color = tick.color ?: labelColor))
                    val textY = (ty - text.size.height - 2f).coerceIn(0f, size.height - text.size.height)
                    drawText(text, topLeft = Offset(6.dp.toPx(), textY))
                }
            }

            val stepX = size.width / (capacity - 1).coerceAtLeast(1)
            series.forEach { s ->
                if (s.values.size < 2) return@forEach
                val path = Path()
                val offset = capacity - s.values.size
                s.values.forEachIndexed { index, value ->
                    val x = (offset + index) * stepX
                    if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
                }
                drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Horizontal gauge with a highlighted "comfort" band, e.g. fiber RX power
 * from -30 to -5 dBm with the ideal range shaded.
 */
@Composable
fun RangeGauge(
    value: Double,
    min: Double,
    max: Double,
    comfortStart: Double,
    comfortEnd: Double,
    markerColor: Color,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val comfort = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        fun x(v: Double) = (((v - min) / (max - min)).coerceIn(0.0, 1.0) * size.width).toFloat()
        val mid = size.height / 2
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
        drawLine(comfort, Offset(x(comfortStart), mid), Offset(x(comfortEnd), mid), strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(markerColor, radius = 8.dp.toPx(), center = Offset(x(value), mid))
    }
}
