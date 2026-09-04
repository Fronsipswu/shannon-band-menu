package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Signal quality family used by the QC Diag Monitor NSG-style bars. */
enum class SignalMetric {
    RSRP,
    RSRQ,
    SINR
}

/** A detail-card value that can optionally be rendered as a signal bar. */
data class SignalDetailValue(
    val text: String,
    val metric: SignalMetric? = null,
    val rawValue: Int? = null,
    val suffix: String? = null
)

data class SignalDetailRow(
    val label: String,
    val values: List<SignalDetailValue>
)

// Solid versions of the QC bar colors (the web version uses these as gradient starts).
private val QC_GREEN = Color(0xFF159B46)
private val QC_LIGHT_GREEN = Color(0xFF65A51F)
private val QC_YELLOW = Color(0xFFBD951D)
private val QC_ORANGE = Color(0xFFB96816)
private val QC_RED = Color(0xFF982D31)

private const val QC_TRACK_COLOR = 0xFF1B1B1B
private const val QC_BORDER_COLOR = 0xFF2E353B

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

private fun signalMetricColor(level: Int?): Color? = when (level) {
    1 -> QC_GREEN
    2 -> QC_LIGHT_GREEN
    3 -> QC_YELLOW
    4 -> QC_ORANGE
    5 -> QC_RED
    else -> null
}

@Composable
fun SignalMetricBar(
    value: SignalDetailValue,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(5.dp)
    val level = value.metric?.let { signalMetricLevel(it, value.rawValue) }
    val fraction = value.metric?.let { signalMetricFraction(it, value.rawValue) }
    val fillColor = signalMetricColor(level)
    val displayText = value.text.ifBlank { "\u2014" }

    Box(
        modifier = modifier
            .height(24.dp)
            .clip(shape)
            .background(Color(QC_TRACK_COLOR))
            .border(1.dp, Color(QC_BORDER_COLOR), shape)
    ) {
        if (fraction != null && fillColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(fillColor, shape)
            )
        }
        Text(
            text = displayText,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            color = Color(0xFFF5F7FA),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(0f, 1f),
                    blurRadius = 2f
                )
            )
        )
    }
}
