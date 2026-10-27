package fronsipswu.shannonbandmenu.diag

import fronsipswu.shannonbandmenu.NetworkCellRole
import fronsipswu.shannonbandmenu.NetworkSubscription
import org.json.JSONObject

/** Keep DIAG tables present during a sampling gap, without retaining old RF values. */
fun diagDisplayCarriers(subscription: NetworkSubscription, snapshot: DiagSnapshot?): List<DiagCarrier> {
    val measured = snapshot?.carriers.orEmpty()
    val placeholders = listOf("LTE", "NR").flatMap { technology ->
        val cells = subscription.cells.filter { it.technology == technology }
            .sortedBy { it.role != NetworkCellRole.PRIMARY }
        cells.mapIndexedNotNull { index, cell ->
            val nr = technology == "NR"
            if (measured.any { it.nr == nr && it.arfcn == cell.channel && it.pci == cell.physicalId }) {
                return@mapIndexedNotNull null
            }
            val identity = JSONObject().apply {
                put("slot", index)
                put("role", if (index == 0) "pcell" else "scell")
                put("arfcn", cell.channel)
                put("pci", cell.physicalId)
                put("band", cell.band)
                put("bandwidth_mhz", cell.bandwidthKhz?.div(1000.0))
            }
            DiagCarrier(nr, identity)
        }
    }
    return (measured + placeholders).sortedWith(compareBy({ it.nr }, { !it.primary }, { it.slot }))
}

/** A brief ambiguous/missing sample must not switch a known SIM back to API tables. */
fun diagDisplayOwner(
    matched: Int?, previous: Int?, subscriptions: List<NetworkSubscription>
): Int? = matched ?: previous?.takeIf { id -> subscriptions.any { it.subscriptionId == id } }
    ?: subscriptions.singleOrNull()?.subscriptionId
