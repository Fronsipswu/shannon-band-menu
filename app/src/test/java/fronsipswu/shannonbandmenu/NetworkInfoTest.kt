package fronsipswu.shannonbandmenu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkInfoTest {

    @Test
    fun lteBandForEarfcn_matchesObservedCarriers() {
        assertEquals(1, lteBandForEarfcn(125))
        assertEquals(1, lteBandForEarfcn(250))
        assertEquals(3, lteBandForEarfcn(1275))
        assertEquals(28, lteBandForEarfcn(9310))
        assertNull(lteBandForEarfcn(-1))
    }

    @Test
    fun lteFrequenciesForEarfcn_matchesRxMonitorValues() {
        val band3 = lteFrequenciesForEarfcn(1275)!!
        assertEquals(1812.5, band3.downlinkMhz, 0.001)
        assertEquals(1717.5, band3.uplinkMhz!!, 0.001)

        val band1 = lteFrequenciesForEarfcn(250)!!
        assertEquals(2135.0, band1.downlinkMhz, 0.001)
        assertEquals(1945.0, band1.uplinkMhz!!, 0.001)

        val band28 = lteFrequenciesForEarfcn(9310)!!
        assertEquals(768.0, band28.downlinkMhz, 0.001)
        assertEquals(713.0, band28.uplinkMhz!!, 0.001)
    }

    @Test
    fun nrFrequencyForArfcn_matchesObservedN41Carrier() {
        assertEquals(2649.75, nrFrequencyForArfcn(529950)!!, 0.001)
    }

    @Test
    fun timingAdvance_matchesObservedDistance() {
        assertEquals(156.25, lteTimingAdvanceMeters(2)!!, 0.001)
        assertNull(lteTimingAdvanceMeters(null))
    }

    @Test
    fun carrierAggregation_usesPCellAndOrderedSCells() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 28, bandwidthKhz = 20_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 3, bandwidthKhz = 10_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 15_000)
        )

        assertEquals(
            "3CA (B28+B3+B1)",
            carrierAggregationLabel(cells, listOf(20_000, 10_000, 15_000))
        )
        assertEquals(
            "45 MHz (20+10+15)",
            totalBandwidthLabel(cells, listOf(20_000, 10_000, 15_000))
        )
    }

    @Test
    fun carrierAggregation_reordersPrimaryCellFirst() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 3, bandwidthKhz = 10_000),
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 28, bandwidthKhz = 20_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 15_000)
        )

        assertEquals(
            "3CA (B28+B3+B1)",
            carrierAggregationLabel(cells, listOf(10_000, 20_000, 15_000))
        )
        assertEquals(
            "45 MHz (20+10+15)",
            totalBandwidthLabel(cells, listOf(10_000, 20_000, 15_000))
        )
    }

    @Test
    fun carrierAggregation_singleCellReturnsNoAndSingleBandwidth() {
        val singleCell = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 28, bandwidthKhz = 20_000)
        )

        assertEquals("No", carrierAggregationLabel(singleCell, listOf(20_000)))
        assertEquals("20 MHz", totalBandwidthLabel(singleCell, listOf(20_000)))
    }

    @Test
    fun nsaBandwidths_areSeparatedByTechnology() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 3, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 10_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )
        val bandwidths = listOf(15_000, 15_000, 10_000, 90_000)
        val lteCells = cells.filter { it.technology == "LTE" }
        val lteBandwidths = lteBandwidthsForNsa(cells, bandwidths)

        assertEquals(listOf(15_000, 15_000, 10_000), lteBandwidths)
        assertEquals("3CA (B3+B1+B1)", carrierAggregationLabel(lteCells, lteBandwidths))
        assertEquals("40 MHz (15+15+10)", totalBandwidthLabel(lteCells, lteBandwidths))
        assertEquals("90 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))
        assertEquals(true, isNrNsa(cells))
    }

    @Test
    fun nrSaCarrierAggregation_usesBandwidthsFormat() {
        val cells = listOf(
            NetworkCell("NR", NetworkCellRole.PRIMARY, 0, true, band = 41),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )
        val bandwidths = listOf(90_000, 20_000)

        assertEquals("2CA (90+20 MHz)", nrSaCarrierAggregationLabel(cells, bandwidths))
    }

    @Test
    fun nrSaCarrierAggregation_singleCarrierReturnsNo() {
        val cells = listOf(
            NetworkCell("NR", NetworkCellRole.PRIMARY, 0, true, band = 41)
        )
        val bandwidths = listOf(90_000)

        assertEquals("No", nrSaCarrierAggregationLabel(cells, bandwidths))
    }

    @Test
    fun lteRssnr_isStoredAsWholeDbWithoutScaling() {
        val cell = NetworkCell(
            technology = "LTE",
            role = NetworkCellRole.PRIMARY,
            simSlot = 0,
            registered = true,
            sinr = 13
        )

        assertEquals(13, cell.sinr)
    }
}
