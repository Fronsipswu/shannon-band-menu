package fronsipswu.shannonbandmenu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalMetricBarTest {

    @Test
    fun rsrpLevels_matchQcThresholds() {
        assertEquals(1, signalMetricLevel(SignalMetric.RSRP, -85))
        assertEquals(2, signalMetricLevel(SignalMetric.RSRP, -86))
        assertEquals(2, signalMetricLevel(SignalMetric.RSRP, -95))
        assertEquals(3, signalMetricLevel(SignalMetric.RSRP, -96))
        assertEquals(3, signalMetricLevel(SignalMetric.RSRP, -105))
        assertEquals(4, signalMetricLevel(SignalMetric.RSRP, -106))
        assertEquals(4, signalMetricLevel(SignalMetric.RSRP, -115))
        assertEquals(5, signalMetricLevel(SignalMetric.RSRP, -116))
    }

    @Test
    fun rsrqLevels_matchQcThresholds() {
        assertEquals(1, signalMetricLevel(SignalMetric.RSRQ, -6))
        assertEquals(2, signalMetricLevel(SignalMetric.RSRQ, -7))
        assertEquals(2, signalMetricLevel(SignalMetric.RSRQ, -10))
        assertEquals(3, signalMetricLevel(SignalMetric.RSRQ, -11))
        assertEquals(3, signalMetricLevel(SignalMetric.RSRQ, -15))
        assertEquals(4, signalMetricLevel(SignalMetric.RSRQ, -16))
        assertEquals(4, signalMetricLevel(SignalMetric.RSRQ, -20))
        assertEquals(5, signalMetricLevel(SignalMetric.RSRQ, -21))
    }

    @Test
    fun sinrLevels_matchQcThresholds() {
        assertEquals(1, signalMetricLevel(SignalMetric.SINR, 22))
        assertEquals(2, signalMetricLevel(SignalMetric.SINR, 21))
        assertEquals(2, signalMetricLevel(SignalMetric.SINR, 15))
        assertEquals(3, signalMetricLevel(SignalMetric.SINR, 14))
        assertEquals(3, signalMetricLevel(SignalMetric.SINR, 10))
        assertEquals(4, signalMetricLevel(SignalMetric.SINR, 9))
        assertEquals(4, signalMetricLevel(SignalMetric.SINR, 3))
        assertEquals(5, signalMetricLevel(SignalMetric.SINR, 2))
    }

    @Test
    fun fractions_useQcRangesAndClamp() {
        assertEquals(0f, signalMetricFraction(SignalMetric.RSRP, -140))
        assertEquals(1f, signalMetricFraction(SignalMetric.RSRP, -70))
        assertEquals(0f, signalMetricFraction(SignalMetric.RSRP, -141))
        assertEquals(1f, signalMetricFraction(SignalMetric.RSRP, -69))

        assertEquals(0f, signalMetricFraction(SignalMetric.RSRQ, -40))
        assertEquals(1f, signalMetricFraction(SignalMetric.RSRQ, 0))
        assertEquals(0f, signalMetricFraction(SignalMetric.SINR, -30))
        assertEquals(1f, signalMetricFraction(SignalMetric.SINR, 40))
    }

    @Test
    fun missingValues_haveNoQualityLevelOrFill() {
        assertNull(signalMetricLevel(SignalMetric.RSRP, null))
        assertNull(signalMetricFraction(SignalMetric.RSRQ, null))
    }
}
