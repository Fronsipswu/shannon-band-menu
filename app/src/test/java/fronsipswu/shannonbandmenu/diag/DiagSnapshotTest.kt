package fronsipswu.shannonbandmenu.diag

import fronsipswu.shannonbandmenu.NetworkCell
import fronsipswu.shannonbandmenu.NetworkCellRole
import fronsipswu.shannonbandmenu.NetworkSubscription
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagSnapshotTest {
    private fun snapshot(rat: String = "NSA", lte: String = "[]", nr: String = "[]") = DiagSnapshot.parse(
        JSONObject("""{"schema":1,"window_s":4,"rat":"$rat",
            "lte":{"dl_mbps":12.5,"ul_mbps":0,"cells":$lte},
            "nr":{"phy_dl_mbps":100,"phy_ul_mbps":null,"cells":$nr}}""")
    )

    private val lteCell = """[{"slot":0,"role":"pcell","arfcn":100,"pci":1,
        "rsrp_dbm":-91.25,"rsrq_db":null,"phy_dl_mbps":0,"modulation_dl_bits":6}]"""

    @Test fun preservesMissingSignalsAndMeasuredZero() {
        val carrier = snapshot(lte = lteCell).carriers.single()
        assertEquals(-91.25, carrier.rsrp!!, 0.001)
        assertNull(carrier.rsrq)
        assertEquals(0.0, carrier.dlMbps!!, 0.001)
        assertNull(carrier.ulMbps)
        assertEquals("64QAM", carrier.modulation)
    }

    @Test fun sumsRatsWithoutCountingInactiveCachedNr() {
        assertEquals(112.5, snapshot().dlMbps!!, 0.001)
        assertEquals(12.5, snapshot("LTE").dlMbps!!, 0.001)
        assertEquals(100.0, snapshot("SA").dlMbps!!, 0.001)
        assertNull(snapshot("idle").dlMbps)
    }

    @Test fun ignoresCachedCarriersOfAnInactiveRat() {
        val nrCell = """[{"slot":0,"role":"pcell","pci":2,"arfcn":600000}]"""
        assertTrue(snapshot("LTE", nr = nrCell).carriers.isEmpty())
        assertTrue(snapshot("SA", lte = lteCell).carriers.isEmpty())
    }

    @Test fun nrServingSinrUsesSsbAndRetainsCsiSeparately() {
        val carrier = snapshot(nr = """[{"slot":1,"role":"scell","ssb_sinr_db":20.25,
            "csi_sinr_db":31,"pdsch_sinr_db":15,"sinr_db":9,"prbs_ul_avg":27}]""")
            .carriers.single()
        assertEquals(20.25, carrier.sinr!!, 0.001)
        assertEquals(31.0, carrier.csiSinr!!, 0.001)
        assertEquals(27.0, carrier.ulRbs!!, 0.001)
        assertEquals("S1", carrier.role)
    }

    private fun subscription(id: Int, channel: Int, pci: Int) = NetworkSubscription(
        id, id - 1, "Test", null, null, "", false, "LTE", "LTE", "Connected", emptyList(),
        listOf(NetworkCell("LTE", NetworkCellRole.PRIMARY, id - 1, true,
            channel = channel, physicalId = pci, cellId = 42))
    )

    @Test fun attributesUnlabelledMetricsOnlyToUniqueServingIdentity() {
        val data = snapshot(lte = lteCell)
        val first = subscription(1, 100, 1)
        val second = subscription(2, 200, 2)
        assertEquals(1, diagSubscription(data, listOf(first, second)))
        assertNull(diagSubscription(data, listOf(first, subscription(2, 100, 1))))
        assertNull(diagSubscription(data, listOf(second, subscription(3, 300, 3))))
    }

    @Test fun mergesServingReadingsWithoutReusingAnotherCellsIdentity() {
        val cell = subscription(1, 100, 1).cells.single()
        assertEquals(-91, diagServingCell(cell, snapshot(lte = lteCell)).rsrp)
        assertEquals(42L, diagServingCell(cell, snapshot(lte = lteCell)).cellId)
        val moved = cell.copy(channel = 200)
        assertEquals(moved, diagServingCell(moved, snapshot(lte = lteCell)))
    }

    @Test fun nsaSamplingGapKeepsDiagColumnsButNeverHoldsOldReadings() {
        val subscription = subscription(1, 100, 1).let {
            it.copy(cells = it.cells + NetworkCell("NR", NetworkCellRole.SECONDARY, 0, true,
                channel = 600000, physicalId = 2))
        }
        val measured = diagDisplayCarriers(subscription, snapshot(lte = lteCell))
        val gap = diagDisplayCarriers(subscription, null)
        assertEquals(measured.map { it.nr to it.arfcn }, gap.map { it.nr to it.arfcn })
        assertEquals(-91.25, measured.first().rsrp!!, 0.001)
        assertNull(gap.first().rsrp)
        assertNull(gap.first().dlMbps)
        assertEquals(1, diagDisplayOwner(null, 1, listOf(subscription, subscription(2, 200, 2))))
    }

    @Test fun expiredSimOwnershipIsNotCarriedOntoAnotherSim() {
        assertNull(diagDisplayOwner(null, 1, listOf(subscription(2, 200, 2), subscription(3, 300, 3))))
    }

    @Test fun resourceBlockScaleRequiresTheReportedNrNumerology() {
        val unknown = DiagCarrier(true, JSONObject("""{"slot":0,"role":"pcell","bandwidth_mhz":50}"""))
        assertNull(maxResourceBlocks(unknown))
        val fr1 = DiagCarrier(true, JSONObject("""{"slot":0,"role":"pcell","band":78,
            "bandwidth_mhz":50,"scs_khz":60}"""))
        val fr2 = DiagCarrier(true, JSONObject("""{"slot":0,"role":"pcell","band":258,
            "bandwidth_mhz":50,"scs_khz":60}"""))
        assertEquals(65.0, maxResourceBlocks(fr1)!!, 0.001)
        assertEquals(66.0, maxResourceBlocks(fr2)!!, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        DiagSnapshot.parse(JSONObject("""{"schema":2}"""))
    }
}
