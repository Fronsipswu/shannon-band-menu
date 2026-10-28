package fronsipswu.shannonbandmenu.network

import fronsipswu.shannonbandmenu.NetworkInfoSnapshot
import fronsipswu.shannonbandmenu.diag.DiagMonitor
import fronsipswu.shannonbandmenu.diag.ThputPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkReplayTest {
    @Test fun replayGraphIncludesFutureSamplesAndPreservesMissingDirections() {
        val first = ThputPoint(1000, 10.0, null)
        val second = ThputPoint(2000, null, 2.0)
        val future = ThputPoint(62000, 30.0, 3.0)
        fun frame(time: Long, points: List<ThputPoint>) = NetworkReplayFrame(
            time, NetworkInfoSnapshot(capturedAtMillis = time),
            DiagMonitor.State(points = points), null, null, emptyMap()
        )
        val frames = listOf(frame(2000, listOf(first, second)),
            frame(62000, listOf(first, second, future)))
        assertEquals(listOf(first, second, future), replayThroughput(frames))
    }

    @Test fun timelineStaysStableWhileLiveHistoryAdvances() {
        val history = ReplayHistory<String>(maxEntries = 2)
        history.append(1000, 10, "first")
        history.append(2000, 10, "second")
        val timeline = history.open()
        history.append(3000, 10, "third")
        assertEquals(listOf("first", "second"), timeline)
        assertEquals(listOf("second", "third"), history.open())
    }

    @Test fun duplicatePollsDoNotFillTheTimeline() {
        val history = ReplayHistory<String>()
        history.append(1100, 10, "android")
        history.append(1200, 10, "diag")
        history.append(2100, 10, "next")
        assertEquals(listOf("android", "next"), history.open())
    }

    @Test fun recordingGapExpiresOldSamples() {
        val history = ReplayHistory<String>(durationMillis = 2000)
        history.append(1000, 10, "before leaving the page")
        history.append(10000, 10, "after returning")
        assertEquals(listOf("after returning"), history.open())
    }

    @Test fun memoryBudgetEvictsOldestAndRejectsOversizedFrames() {
        val history = ReplayHistory<String>(maxBytes = 20)
        history.append(1000, 10, "first")
        history.append(2000, 15, "second")
        assertEquals(listOf("second"), history.open())
        history.append(3000, 25, "too large")
        assertEquals(emptyList<String>(), history.open())
    }
}
