package fronsipswu.shannonbandmenu.diag

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WcdmaSnapshotTest {
    private fun snapshot(metrics: String) = DiagSnapshot.parse(JSONObject("""
        {"schema":1,"window_s":7,"rat":"WCDMA",
         "lte":{"dl_mbps":999,"ul_mbps":999,"cells":[]},
         "nr":{"phy_dl_mbps":999,"phy_ul_mbps":999,"cells":[]},
         "wcdma":$metrics}
    """))

    @Test fun wcdmaRatesDoNotIncludeStaleLteOrNr() {
        val reading = snapshot("""{"dl_mbps":1.25,"ul_mbps":0.5,"psc":83,"cqi":25.4}""")
        assertEquals(1.25, reading.dlMbps!!, 0.0)
        assertEquals(0.5, reading.ulMbps!!, 0.0)
        assertTrue(reading.carriers.isEmpty())
        assertEquals(83, reading.wcdma?.psc)
        assertEquals(25.4, reading.wcdma!!.cqi!!, 0.0)
    }

    @Test fun missingReportsDoNotBecomeZeroTraffic() {
        val reading = snapshot("""{"dl_mbps":null,"ul_mbps":null,"psc":null}""")
        assertNull(reading.dlMbps)
        assertNull(reading.ulMbps)
        assertNull(reading.wcdma?.psc)
    }
}
