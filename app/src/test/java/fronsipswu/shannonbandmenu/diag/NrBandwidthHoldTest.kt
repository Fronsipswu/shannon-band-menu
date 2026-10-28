package fronsipswu.shannonbandmenu.diag

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NrBandwidthHoldTest {
    private fun carrier(width: Double?, arfcn: Int = 520000, pci: Int = 100,
                        slot: Int = 1, band: Int = 41): DiagCarrier = DiagCarrier(true,
        JSONObject().put("slot", slot).put("role", if (slot == 0) "pcell" else "scell")
            .put("band", band).put("arfcn", arfcn).put("pci", pci)
            .put("bandwidth_mhz", width ?: JSONObject.NULL))

    @Test fun holdsAcrossMissingSamplesSlotMovesAndTemporaryAbsence() {
        val hold = NrBandwidthHold()
        hold.apply(listOf(carrier(100.0)))
        assertEquals(100.0, hold.apply(listOf(carrier(null, slot = 0))).single().bandwidth)
        hold.apply(emptyList())
        assertEquals(100.0, hold.apply(listOf(carrier(null, slot = 3))).single().bandwidth)
    }

    @Test fun requiresBothArfcnAndPci() {
        val hold = NrBandwidthHold()
        hold.apply(listOf(carrier(100.0)))
        assertNull(hold.apply(listOf(carrier(null, pci = 101))).single().bandwidth)
        assertNull(hold.apply(listOf(carrier(null, arfcn = 530000))).single().bandwidth)
    }

    @Test fun latestValidWidthReplacesEvenWhenSmaller() {
        val hold = NrBandwidthHold()
        hold.apply(listOf(carrier(100.0)))
        hold.apply(listOf(carrier(80.0)))
        assertEquals(80.0, hold.apply(listOf(carrier(null))).single().bandwidth)
        assertEquals(80.0, hold.apply(listOf(carrier(0.0))).single().bandwidth)
    }

    @Test fun conflictingBandInvalidatesHold() {
        val hold = NrBandwidthHold()
        hold.apply(listOf(carrier(100.0)))
        assertNull(hold.apply(listOf(carrier(null, band = 38))).single().bandwidth)
    }

    @Test fun separateInstancesDoNotShareCarrierData() {
        val first = NrBandwidthHold()
        val second = NrBandwidthHold()
        first.apply(listOf(carrier(100.0)))
        assertNull(second.apply(listOf(carrier(null))).single().bandwidth)
    }

    @Test fun carrierIdentityCacheIsBounded() {
        val hold = NrBandwidthHold()
        for (pci in 0..128) hold.apply(listOf(carrier(100.0, pci = pci)))
        assertNull(hold.apply(listOf(carrier(null, pci = 0))).single().bandwidth)
        assertEquals(100.0, hold.apply(listOf(carrier(null, pci = 128))).single().bandwidth)
    }
}
