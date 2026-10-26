package fronsipswu.shannonbandmenu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Signal quality family used by the QC Diag Monitor NSG-style bars. */
enum class SignalMetric {
    RSRP,
    RSRQ,
    SINR
}

/** Returns the QC level, where 1 is best and 5 is worst. */
fun signalMetricLevel(metric: SignalMetric, value: Int?): Int? {
    if (value == null) return null
    return when (metric) {
        SignalMetric.RSRP -> when {
            value >= -85 -> 1
            value >= -95 -> 2
            value >= -105 -> 3
            value >= -115 -> 4
            else -> 5
        }
        SignalMetric.RSRQ -> when {
            value >= -6 -> 1
            value >= -10 -> 2
            value >= -15 -> 3
            value >= -20 -> 4
            else -> 5
        }
        SignalMetric.SINR -> when {
            value >= 22 -> 1
            value >= 15 -> 2
            value >= 10 -> 3
            value >= 3 -> 4
            else -> 5
        }
    }
}

/** Returns the QC normalized fill fraction, clamped to the web implementation's range. */
fun signalMetricFraction(metric: SignalMetric, value: Int?): Float? {
    if (value == null) return null
    val (minimum, maximum) = when (metric) {
        SignalMetric.RSRP -> -140 to -70
        SignalMetric.RSRQ -> -40 to 0
        SignalMetric.SINR -> -30 to 40
    }
    return ((value - minimum).toFloat() / (maximum - minimum))
        .coerceIn(0f, 1f)
}

/** The values where the level colour steps, best first (the bar's tick marks). */
private fun signalMetricThresholds(metric: SignalMetric): List<Int> = when (metric) {
    SignalMetric.RSRP -> listOf(-85, -95, -105, -115)
    SignalMetric.RSRQ -> listOf(-6, -10, -15, -20)
    SignalMetric.SINR -> listOf(22, 15, 10, 3)
}

// Mid-tone level colours, readable on both the light and the dark track.
private val LEVEL_COLORS = listOf(
    Color(0xFF1E9E50), Color(0xFF7CB342), Color(0xFFEAB01F), Color(0xFFF07E26), Color(0xFFE0453A)
)

private fun signalMetricColor(level: Int?): Color? =
    level?.let { LEVEL_COLORS[(it - 1).coerceIn(0, LEVEL_COLORS.lastIndex)] }

private val LEVEL_WORDS = listOf("excellent", "good", "fair", "poor", "bad")

/**
 * One metric row: label, bar, value. An unavailable value renders an empty
 * track and a dash, never a red zero bar.
 */
@Composable
fun SignalBar(
    label: String,
    value: Int?,
    metric: SignalMetric,
    unit: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 64.dp,
    valueWidth: Dp = 72.dp,
    barHeight: Dp = 10.dp
) {
    val level = signalMetricLevel(metric, value)
    val targetFill = signalMetricFraction(metric, value) ?: 0f
    val targetColor = signalMetricColor(level) ?: Color.Transparent
    val fill by animateFloatAsState(targetFill, tween(450), label = "signalFill")
    val color by animateColorAsState(targetColor, tween(450), label = "signalColor")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val valueText = value?.let { "$it $unit" } ?: "—"
    val spoken = if (value == null) "$label unavailable"
    else "$label $valueText${level?.let { ", " + LEVEL_WORDS[it - 1] }.orEmpty()}"

    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(modifier = Modifier.weight(1f).height(barHeight)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            // Threshold ticks: where the colour steps.
            signalMetricThresholds(metric).forEach { threshold ->
                val x = size.width * (signalMetricFraction(metric, threshold) ?: 0f)
                drawRect(
                    tick,
                    topLeft = Offset(x - 0.5f, size.height * 0.2f),
                    size = Size(1f, size.height * 0.6f)
                )
            }
            // A real reading below the scale still shows a stub, so it never
            // looks like "unavailable" (an empty track).
            if (value != null || fill > 0f) {
                drawRoundRect(
                    color,
                    size = Size(size.width * fill.coerceAtLeast(size.height / size.width), size.height),
                    cornerRadius = radius
                )
            }
        }
        Text(
            valueText,
            modifier = Modifier.width(valueWidth),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A table cell: the value on a level-coloured bar that fades toward its tip.
 * An unavailable value keeps the empty track, so a cell looks the same
 * whether or not the value is there.
 */
@Composable
fun CellValueBar(
    value: Int?,
    metric: SignalMetric,
    maxWidth: Dp,
    style: TextStyle
) {
    val shape = RoundedCornerShape(4.dp)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fraction = signalMetricFraction(metric, value)
    val color = signalMetricColor(signalMetricLevel(metric, value))
    Box(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(track)
            .drawBehind {
                if (fraction != null && color != null) {
                    val width = size.width * fraction
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(color, color.copy(alpha = 0.65f)),
                            endX = width
                        ),
                        size = Size(width, size.height)
                    )
                }
            }
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value?.toString() ?: "–",
            style = style,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}
