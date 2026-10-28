package fronsipswu.shannonbandmenu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TxPowerScaleTest {
    @Test fun qbmTransmitPowerBoundsAndClamping() {
        assertEquals(0f, txPowerFraction(-40.0), 0.0001f)
        assertEquals(0f, txPowerFraction(-27.0), 0.0001f)
        assertEquals(27f / 53f, txPowerFraction(0.0), 0.0001f)
        assertEquals(1f, txPowerFraction(26.0), 0.0001f)
        assertEquals(1f, txPowerFraction(30.0), 0.0001f)
    }

    @Test fun qbmTransmitPowerColorsBecomeWorseAsPowerRises() {
        assertEquals(0, txPowerLevel(0.0))
        assertEquals(1, txPowerLevel(10.0))
        assertEquals(2, txPowerLevel(17.0))
        assertEquals(3, txPowerLevel(23.0))
        assertEquals(4, txPowerLevel(24.0))
    }
}
