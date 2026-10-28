package fronsipswu.shannonbandmenu.diag

import fronsipswu.shannonbandmenu.NetworkCellRole
import fronsipswu.shannonbandmenu.NetworkCell
import fronsipswu.shannonbandmenu.NetworkSubscription
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** A completed helper window. Missing measurements stay null, including rates. */
data class DiagSnapshot(
    val window: Long,
    val rat: String,
    val carriers: List<DiagCarrier>,
    val dlMbps: Double?,
    val ulMbps: Double?,
    val wcdma: WcdmaSnapshot? = null
) {
    companion object {
        fun parse(json: JSONObject): DiagSnapshot {
            require(json.optInt("schema") == 1) { "Unsupported DIAG schema" }
            val rat = json.optString("rat")
            require(rat in setOf("LTE", "NSA", "SA", "WCDMA", "idle")) { "Unsupported DIAG mode" }
            val wcdma = json.optJSONObject("wcdma").takeIf { rat == "WCDMA" }
            val lte = json.getJSONObject("lte")
            val nr = json.getJSONObject("nr")
            val ltePresent = rat == "LTE" || rat == "NSA"
            val nrPresent = rat == "SA" || rat == "NSA"
            val carriers = buildList {
                if (ltePresent) addAll(parseCells(lte.optJSONArray("cells"), false))
                if (nrPresent) addAll(parseCells(nr.optJSONArray("cells"), true))
            }
            return DiagSnapshot(
                window = json.getLong("window_s"),
                rat = rat,
                carriers = carriers,
                dlMbps = if (rat == "WCDMA") wcdma?.number("dl_mbps") else sumKnown(
                    lte.number("dl_mbps").takeIf { ltePresent },
                    nr.number("phy_dl_mbps").takeIf { nrPresent }
                ),
                ulMbps = if (rat == "WCDMA") wcdma?.number("ul_mbps") else sumKnown(
                    lte.number("ul_mbps").takeIf { ltePresent },
                    nr.number("phy_ul_mbps").takeIf { nrPresent }
                ),
                wcdma = wcdma?.let { WcdmaSnapshot(it) }
            )
        }

        private fun parseCells(array: JSONArray?, nr: Boolean): List<DiagCarrier> =
            (0 until (array?.length() ?: 0)).map { DiagCarrier(nr, array!!.getJSONObject(it)) }

        private fun sumKnown(first: Double?, second: Double?): Double? =
            if (first == null && second == null) null else (first ?: 0.0) + (second ?: 0.0)
    }
}

/** WCDMA has its own schema; its PSC and CPICH values are never LTE PCI/RSRP. */
data class WcdmaSnapshot(private val json: JSONObject) {
    val psc = json.number("psc")?.toInt()
    val rscp = json.number("cpich_rscp_dbm")
    val ecno = json.number("cpich_ecno_db")
    val cqi = json.number("cqi")
    val qpsk = json.number("qpsk_pct")
    val qam16 = json.number("qam16_pct")
    val qam64 = json.number("qam64_pct")
    val scchSuccess = json.number("hs_scch_success_pct")
}

/** One radio carrier; all field names and units follow the native schema. */
data class DiagCarrier(val nr: Boolean, private val json: JSONObject) {
    val slot = json.getInt("slot")
    val primary = json.optString("role") == "pcell"
    val band = value("band")?.toInt()
    val arfcn = value("arfcn")?.toInt()
    val pci = value("pci")?.toInt()
    val bandwidth = value("bandwidth_mhz")
    val scs = value("scs_khz")
    val chainRsrp = json.numbers("chain_rsrp_dbm")
    // Channel configuration can lag receiver changes. Prefer chains with a
    // current valid RSRP measurement, independent of CSI rank or layer count.
    val rx = if (nr) chainRsrp.take(4).count { it != null && it > -140 && it < -20 }
        .takeIf { it > 0 } ?: value("rx")?.toInt() else value("rx")?.toInt()
    val csiPorts = value("csi_ports")?.toInt()?.takeIf { it in 1..32 }
    val downlinkScheduled = json.optBoolean("dl_scheduled", false)
    val tx = value("tx")?.toInt()
    val rsrp = value("rsrp_dbm")
    val rsrq = value("rsrq_db")
    val sinr = if (nr) value("ssb_sinr_db") ?: value("sinr_db") else value("sinr_db")
    val csiSinr = value("csi_sinr_db")
    val pdschSinr = value("pdsch_sinr_db")
    val rank = value("rank") ?: value("layers")
    val mcs = value("mcs_dl")
    val rbs = value("prbs_dl")
    val bler = value("bler_dl_pct")
    val cqi = value("cqi")
    val dlMbps = value("phy_dl_mbps")
    val ulMbps = value("phy_ul_mbps")
    val ulRbs = value(if (nr) "prbs_ul_avg" else "prbs_ul_grant")
    val ulMcs = value("mcs_ul")
    val ulBler = value("bler_ul_pct")
    val ulRank = value("layers_ul")?.takeIf { it == 1.0 || it == 2.0 }
    val pusch = value("pusch_dbm")
    val pucch = value("pucch_dbm")
    val mode = value("transmission_mode")?.toInt()
    val beam = value("beam")?.toInt()
    val modulation = modulation(value("modulation_dl_bits"))
    val ulModulation = modulation(value("modulation_ul_bits"))
    val chainRsrq = json.numbers("chain_rsrq_db")
    val label get() = band?.let { if (nr) "n$it" else "B$it" }
    val role get() = if (primary) "P" else "S$slot"
    val hasUplink = json.optBoolean("uplink_active", false)

    private fun value(name: String) = json.number(name)

    internal fun withCsiPorts(ports: Int?): DiagCarrier =
        if (ports == csiPorts) this
        else DiagCarrier(nr, JSONObject(json.toString()).put("csi_ports", ports ?: JSONObject.NULL))

    internal fun withBandwidth(width: Double?): DiagCarrier =
        if (width == bandwidth) this
        else DiagCarrier(nr, JSONObject(json.toString()).put("bandwidth_mhz", width ?: JSONObject.NULL))

    private fun modulation(bits: Double?): String? = when (bits?.toInt()) {
        2 -> "QPSK"
        4 -> "16QAM"
        6 -> "64QAM"
        8 -> "256QAM"
        10 -> "1024QAM"
        else -> null
    }
}

data class ThputPoint(val t: Long, val mbps: Double?, val ulMbps: Double?)

/** Unlabelled modem data must never be mirrored onto both SIMs. */
fun diagSubscription(snapshot: DiagSnapshot, subscriptions: List<NetworkSubscription>): Int? {
    if (subscriptions.size == 1) return subscriptions.single().subscriptionId
    if (snapshot.rat == "WCDMA") {
        // A second SIM on LTE/NR does not make the sole WCDMA subscription
        // ambiguous. Two WCDMA subscriptions still need better DM identity.
        val candidates = subscriptions.filter { subscription ->
            subscription.dataNetwork in setOf("UMTS", "HSDPA", "HSUPA", "HSPA", "HSPA+") ||
                subscription.cells.any { it.role == NetworkCellRole.PRIMARY && it.technology == "WCDMA" }
        }
        val candidate = candidates.singleOrNull() ?: return null
        val psc = snapshot.wcdma?.psc
        val cells = candidate.cells.filter { it.role == NetworkCellRole.PRIMARY &&
            it.technology == "WCDMA" && it.physicalId != null }
        return candidate.subscriptionId.takeIf { psc == null || cells.isEmpty() || cells.any { it.physicalId == psc } }
    }
    val matches = subscriptions.filter { subscription ->
        subscription.cells.any { cell ->
            cell.role == NetworkCellRole.PRIMARY && snapshot.carriers.any { carrier ->
                cell.technology == (if (carrier.nr) "NR" else "LTE") &&
                    cell.channel != null && cell.physicalId != null &&
                    cell.channel == carrier.arfcn && cell.physicalId == carrier.pci
            }
        }
    }
    return matches.singleOrNull()?.subscriptionId
}

/** Preserve Android-only identity fields only while they still name this carrier. */
fun diagServingCell(cell: NetworkCell, snapshot: DiagSnapshot?): NetworkCell {
    snapshot?.wcdma?.takeIf { snapshot.rat == "WCDMA" && cell.technology == "WCDMA" &&
        it.psc != null && it.psc == cell.physicalId }?.let {
        return cell.copy(rsrp = it.rscp?.roundToInt(), sinr = it.ecno?.roundToInt())
    }
    val match = snapshot?.carriers?.firstOrNull {
        it.primary && (if (it.nr) "NR" else "LTE") == cell.technology &&
            it.arfcn == cell.channel && it.pci == cell.physicalId
    } ?: return cell
    return cell.copy(
        rsrp = match.rsrp?.roundToInt(), rsrq = match.rsrq?.roundToInt(), sinr = match.sinr?.roundToInt(),
        bandwidthKhz = match.bandwidth?.let { (it * 1000).roundToInt() }, band = match.band ?: cell.band
    )
}

private fun JSONObject.number(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name).takeIf { it.isFinite() }

private fun JSONObject.numbers(name: String): List<Double?> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { index ->
        if (array.isNull(index)) null else array.optDouble(index).takeIf { it.isFinite() }
    }
}
