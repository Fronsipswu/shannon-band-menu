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
        assertEquals("No", carrierAggregationLabel(singleCell, listOf(20_000, 90_000)))
        assertEquals("20 MHz", totalBandwidthLabel(singleCell, listOf(20_000)))
        assertEquals("20 MHz", totalBandwidthLabel(singleCell, listOf(20_000, 90_000)))
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
        assertEquals("15+15+10 MHz", lteBandwidthLabelForNsa(cells, bandwidths))
        assertEquals("90 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))
        assertEquals(true, isNrNsa(cells))

        val resolved = resolveNrBandwidths(cells, bandwidths)
        assertEquals(90_000, resolved[3].bandwidthKhz)
    }

    @Test
    fun nsaBandwidths_multipleLteServingCellsWithOnlyPrimaryAndNrReportedInBandwidths() {
        // Real-world scenario on user's device: 2 LTE cells (PCell B3 + SCell B1) and 1 NR SCell,
        // but ServiceState.cellBandwidths only reports [15000, 90000].
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 3, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 10_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )
        val bandwidths = listOf(15_000, 90_000)
        val lteCells = cells.filter { it.technology == "LTE" }
        val lteBandwidths = lteBandwidthsForNsa(cells, bandwidths)

        assertEquals(listOf(15_000), lteBandwidths)
        assertEquals("2CA (B3+B1)", carrierAggregationLabel(lteCells, lteBandwidths))
        assertEquals("15+10 MHz", lteBandwidthLabel(cells, bandwidths))
        assertEquals("90 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))
        assertEquals(true, isNrNsa(cells))

        val resolved = resolveNrBandwidths(cells, bandwidths)
        assertEquals(90_000, resolved[2].bandwidthKhz)
    }

    @Test
    fun nsaBandwidths_whenNrBandwidthIs20MHzOrLess() {
        // Low-band NR scenario: LTE B3 (15 MHz) + NR n28 (20 MHz) -> [15000, 20000]
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 3, bandwidthKhz = 15_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false, band = 28)
        )
        val bandwidths = listOf(15_000, 20_000)
        val lteCells = cells.filter { it.technology == "LTE" }
        val lteBandwidths = lteBandwidthsForNsa(cells, bandwidths)

        assertEquals(listOf(15_000), lteBandwidths)
        assertEquals("15 MHz", lteBandwidthLabel(cells, bandwidths))
        assertEquals("20 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))

        val resolved = resolveNrBandwidths(cells, bandwidths)
        assertEquals(20_000, resolved[1].bandwidthKhz)
    }

    @Test
    fun nsaBandwidths_multipleLteCellsWhenNrIs20MHz() {
        // LTE B3 (15 MHz) + LTE B1 (10 MHz) + NR n28 (20 MHz), with [15000, 20000] in bandwidths
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 3, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 10_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false, band = 28)
        )
        val bandwidths = listOf(15_000, 20_000)
        val lteCells = cells.filter { it.technology == "LTE" }
        val lteBandwidths = lteBandwidthsForNsa(cells, bandwidths)

        assertEquals(listOf(15_000), lteBandwidths)
        assertEquals("15+10 MHz", lteBandwidthLabel(cells, bandwidths))
        assertEquals("20 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))

        val resolved = resolveNrBandwidths(cells, bandwidths)
        assertEquals(20_000, resolved[2].bandwidthKhz)
    }

    @Test
    fun nsaBandwidths_matchesObservedThreeLteAndTwoNrCarriers() {
        // Live 3-1-1_n28-n41 capture: CellInfo exposes all three LTE widths but
        // ServiceState contains only the LTE PCell followed by both NR widths.
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, band = 3, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 10_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, band = 1, bandwidthKhz = 15_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )
        val bandwidths = listOf(15_000, 90_000, 20_000)

        assertEquals(
            listOf(15_000) to listOf(90_000, 20_000),
            splitNsaBandwidths(cells, bandwidths)
        )
        assertEquals("15+10+15 MHz", lteBandwidthLabel(cells, bandwidths))
        assertEquals("90+20 MHz", bandwidthLabelForTechnology("NR", cells, bandwidths))
    }

    @Test
    fun nsaBandwidths_preservesLowBandwidthNrAlongsideWideNr() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, bandwidthKhz = 15_000),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )

        assertEquals(
            listOf(15_000) to listOf(100_000, 20_000),
            splitNsaBandwidths(cells, listOf(15_000, 100_000, 20_000))
        )
    }

    @Test
    fun nsaBandwidths_doesNotGuessLowNrWhenLteBandwidthIsUnknown() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )

        assertEquals(
            listOf(15_000, 20_000) to emptyList<Int>(),
            splitNsaBandwidths(cells, listOf(15_000, 20_000))
        )
        assertEquals("Unknown", bandwidthLabelForTechnology("NR", cells, listOf(15_000, 20_000)))
    }

    @Test
    fun nsaBandwidths_acceptsWideNrWhenLteBandwidthIsUnknown() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )

        assertEquals(
            listOf(15_000) to listOf(90_000),
            splitNsaBandwidths(cells, listOf(15_000, 90_000))
        )
    }

    @Test
    fun lteOnlyBandwidths_areNeverSplitAsNr() {
        val cells = listOf(
            NetworkCell("LTE", NetworkCellRole.PRIMARY, 0, true, bandwidthKhz = 15_000),
            NetworkCell("LTE", NetworkCellRole.SECONDARY, 0, false, bandwidthKhz = 10_000)
        )
        val bandwidths = listOf(15_000, 10_000)

        assertEquals(bandwidths to emptyList<Int>(), splitNsaBandwidths(cells, bandwidths))
        assertEquals(bandwidths, lteBandwidthsForNsa(cells, bandwidths))
        assertEquals("Unknown", bandwidthLabelForTechnology("NR", cells, bandwidths))
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

    @Test
    fun resolveNrBandwidths_assignsFirstBandwidthToPrimaryCell() {
        val cells = listOf(
            NetworkCell("NR", NetworkCellRole.PRIMARY, 0, true, band = 41),
            NetworkCell("NR", NetworkCellRole.SECONDARY, 0, false)
        )
        val bandwidths = listOf(90_000, 20_000)
        val resolved = resolveNrBandwidths(cells, bandwidths)

        assertEquals(90_000, resolved[0].bandwidthKhz)
        assertEquals(20_000, resolved[1].bandwidthKhz)
    }
}
