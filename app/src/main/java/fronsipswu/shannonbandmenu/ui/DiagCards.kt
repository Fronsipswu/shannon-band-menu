package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fronsipswu.shannonbandmenu.diag.DiagCarrier
import fronsipswu.shannonbandmenu.diag.maxResourceBlocks
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun DiagCarrierCard(title: String, carriers: List<DiagCarrier>, uplink: Boolean = false) {
    DiagTitle(title)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DiagTable(carriers, uplink)
        }
    }
}

@Composable
internal fun DiagTitle(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Same per-carrier row order as the reference page, without NR port counts. */
@Composable
private fun DiagTable(carriers: List<DiagCarrier>, uplink: Boolean) = BoxWithConstraints {
    val columnWidth = (maxWidth - 64.dp) / maxOf(4, carriers.size)
    val base = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val digits = maxOf(6, carriers.maxOfOrNull { it.arfcn?.toString()?.length ?: 0 } ?: 0)
    val widest = measurer.measure("8".repeat(digits), base.copy(fontWeight = FontWeight.SemiBold)).size.width
    val available = with(LocalDensity.current) { (columnWidth - 6.dp).toPx() }
    val textSize = base.fontSize * (available / widest.coerceAtLeast(1)).coerceIn(0.1f, 1f)
    val foreground = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = foreground.copy(alpha = 0.08f)
    @Composable
    fun text(value: String?, color: Color = foreground, bold: Boolean = false) {
        Text(value ?: "–", color = if (value == null) muted else color, fontSize = textSize,
            lineHeight = textSize * (4f / 3f),
            fontWeight = if (bold) FontWeight.SemiBold else null, maxLines = 1, softWrap = false)
    }
    @Composable
    fun row(label: String, cell: @Composable (DiagCarrier) -> Unit) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = muted)
            carriers.forEach { carrier ->
                Box(Modifier.width(columnWidth).padding(horizontal = 3.dp), Alignment.Center) { cell(carrier) }
            }
        }
    }
    @Composable
    fun metric(value: Double?, kind: SignalMetric) {
        DiagValueBar(value, signalMetricFraction(kind, value?.roundToInt()),
            signalColor(kind, value), textSize.value)
    }
    @Composable
    fun grant(value: Double?, maximum: Double?) {
        DiagValueBar(value, value?.let { v -> maximum?.let { (v / it).toFloat().coerceIn(0f, 1f) } },
            Color(0xFF2988D8), textSize.value, decimals = 0)
    }
    @Composable
    fun bler(value: Double?) {
        val color = when {
            value == null -> foreground
            value < 5 -> Color(0xFF1E9E50)
            value < 10 -> Color(0xFFEAB01F)
            else -> Color(0xFFE0453A)
        }
        DiagValueBar(value, value?.let { (it / 100).toFloat().coerceIn(0f, 1f) }, color,
            textSize.value, suffix = "%")
    }
    val nr = carriers.all { it.nr }
    Column {
        row("") { carrier ->
            val role = if (uplink && carrier.primary && carriers.any { it.nr } && carriers.any { !it.nr }) {
                if (carrier.nr) "SCG" else "MCG"
            } else carrier.role
            text(role, if (carrier.primary) MaterialTheme.colorScheme.primary else muted, bold = true)
        }
        row("Band") { text(it.label, bandColor(it.label) ?: foreground, bold = true) }
        row("BW") { text(number(it.bandwidth, 0)) }
        if (uplink) {
            row("RBs") { grant(it.ulRbs, maxResourceBlocks(it)) }
            row("MCS") { grant(it.ulMcs, 31.0) }
            row("Mod") { text(it.ulModulation?.removeSuffix("QAM")) }
            if (nr) {
                row("Rank") { text(number(it.ulRank, 0)) }
            }
            row("PUSCH") { power ->
                DiagValueBar(power.pusch, power.pusch?.let { ((it + 40) / 70).toFloat().coerceIn(0f, 1f) },
                    MaterialTheme.colorScheme.primary, textSize.value)
            }
            row("PUCCH") { power ->
                DiagValueBar(power.pucch, power.pucch?.let { ((it + 40) / 70).toFloat().coerceIn(0f, 1f) },
                    MaterialTheme.colorScheme.primary, textSize.value)
            }
            row("BLER") { bler(it.ulBler) }
            row("PHY UL") { text(number(it.ulMbps), UplinkColor, bold = true) }
        } else {
            row(if (nr) "NARFCN" else "EARFCN") { text(it.arfcn?.toString()) }
            row("PCI") { text(it.pci?.toString()) }
            row("RSRP") { metric(it.rsrp, SignalMetric.RSRP) }
            row("RSRQ") { metric(it.rsrq, SignalMetric.RSRQ) }
            row("SINR") { metric(it.sinr, SignalMetric.SINR) }
            if (nr) {
                row("CSINR") { metric(it.csiSinr, SignalMetric.SINR) }
                row("PDSINR") { metric(it.pdschSinr, SignalMetric.SINR) }
                row("Port/Rx") {
                    text(if (it.csiPorts == null && it.rx == null) null
                        else "${it.csiPorts ?: "–"}×${it.rx ?: "–"}")
                }
            } else {
                row("Tx/Rx") {
                    text(if (it.tx == null && it.rx == null) null else "${it.tx ?: "–"}×${it.rx ?: "–"}")
                }
            }
            row("Rank") { text(number(it.rank, 0)) }
            row("CQI") { text(number(it.cqi, 0)) }
            row("RBs") { grant(it.rbs, maxResourceBlocks(it)) }
            row("MCS") { grant(it.mcs, 31.0) }
            row("BLER") { bler(it.bler) }
            row("Mod") { text(it.modulation?.removeSuffix("QAM")) }
            row("PHY DL") { text(number(it.dlMbps), DownlinkColor, bold = true) }
        }
    }
}

/** Missing data retains its track; fractional radio readings keep their precision. */
@Composable
private fun DiagValueBar(
    value: Double?, fraction: Float?, color: Color, textSize: Float, decimals: Int = 1,
    suffix: String = ""
) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(Modifier.fillMaxWidth(0.88f).clip(RoundedCornerShape(4.dp)).background(track).drawBehind {
        fraction?.let { fill ->
            val width = size.width * fill
            if (width > 0f) drawRect(
                Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)), endX = width),
                size = Size(width, size.height)
            )
        }
    }.padding(vertical = 1.dp), Alignment.Center) {
        Text(number(value, decimals)?.let { it + suffix } ?: "–",
            fontSize = textSize.sp, maxLines = 1, softWrap = false,
            lineHeight = (textSize * (4f / 3f)).sp,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface)
    }
}

private fun signalColor(metric: SignalMetric, value: Double?): Color =
    when (signalMetricLevel(metric, value?.roundToInt())) {
        1 -> Color(0xFF1E9E50)
        2 -> Color(0xFF7CB342)
        3 -> Color(0xFFEAB01F)
        4 -> Color(0xFFF07E26)
        else -> Color(0xFFE0453A)
    }

private fun number(value: Double?, decimals: Int = 1): String? =
    value?.let { String.format(Locale.US, "%.${decimals}f", it) }

/** Per-carrier chains, separated by RAT; absent chain metrics are not inferred. */
@Composable
internal fun DiagAntennaCard(carriers: List<DiagCarrier>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text("Antenna measurements", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(if (expanded) "−" else "+")
        }
        if (expanded) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            carriers.forEach { carrier ->
                Text(listOfNotNull(if (carrier.nr) "NR" else "LTE", carrier.role, carrier.label,
                    carrier.arfcn?.toString(), carrier.pci?.let { "PCI $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium)
                val chains = maxOf(carrier.rx ?: 0, carrier.chainRsrp.size, carrier.chainRsrq.size)
                    .coerceIn(1, 4)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("", "RSRP", "RSRQ", "SINR").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                repeat(chains) { chain ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("RX$chain", number(carrier.chainRsrp.getOrNull(chain)),
                            number(carrier.chainRsrq.getOrNull(chain)), null).forEach { value ->
                            Text(value ?: "–", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
