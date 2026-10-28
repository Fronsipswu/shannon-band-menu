package fronsipswu.shannonbandmenu.diag

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NrRxCountTest {
    private fun carrier(chains: List<Double?>?, configured: Int? = 2, nr: Boolean = true,
                        rank: Int = 4): DiagCarrier {
        val json = JSONObject().put("slot", 0).put("role", "pcell")
            .put("rx", configured ?: JSONObject.NULL).put("rank", rank)
        if (chains != null) json.put("chain_rsrp_dbm", JSONArray().apply {
            chains.forEach { put(it ?: JSONObject.NULL) }
        })
        return DiagCarrier(nr, json)
    }

    @Test fun freshFourChainReportOverridesConfiguredTwoRx() {
        assertEquals(4, carrier(listOf(-90.0, -91.0, -92.0, -93.0)).rx)
    }

    @Test fun dropsBackToOneMeasuredChainWhileIdle() {
        val chains = listOf(-90.0, null, null, null)
        assertEquals(1, carrier(chains, configured = 4).rx)
    }

    @Test fun countsMeasurementsRatherThanHighestChainIndex() {
        assertEquals(2, carrier(listOf(-90.0, null, -95.0, null)).rx)
    }

    @Test fun missingAndInvalidChainReportsUseConfiguration() {
        assertEquals(2, carrier(null).rx)
        assertEquals(2, carrier(listOf(null, -204.0, 0.0, -140.0)).rx)
        assertNull(carrier(listOf(null, null, null, null), configured = null).rx)
    }

    @Test fun rankDoesNotSetReceiverCount() {
        assertEquals(1, carrier(listOf(-90.0, null, null, null), rank = 4).rx)
        assertEquals(4, carrier(listOf(-90.0, -91.0, -92.0, -93.0), rank = 1).rx)
    }

    @Test fun lteKeepsItsExplicitReceiverCount() {
        assertEquals(2, carrier(listOf(-90.0, -91.0, -92.0, -93.0), nr = false).rx)
    }
}
