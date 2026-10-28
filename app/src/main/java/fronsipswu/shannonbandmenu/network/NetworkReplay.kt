package fronsipswu.shannonbandmenu.network

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fronsipswu.shannonbandmenu.NetworkInfoSnapshot
import fronsipswu.shannonbandmenu.NetworkInfoSource
import fronsipswu.shannonbandmenu.diag.DiagCarrier
import fronsipswu.shannonbandmenu.diag.DiagMonitor
import fronsipswu.shannonbandmenu.diag.ThputPoint
import fronsipswu.shannonbandmenu.diag.diagDisplayCarriers
import fronsipswu.shannonbandmenu.diag.diagDisplayOwner
import fronsipswu.shannonbandmenu.diag.diagSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The values actually displayed, including the already-resolved CSI port hold. */
data class NetworkReplayFrame(
    val elapsedMillis: Long,
    val network: NetworkInfoSnapshot,
    val diag: DiagMonitor.State,
    val matchedOwner: Int?,
    val displayOwner: Int?,
    val carriers: Map<Int, List<DiagCarrier>>
) {
    // Conservative accounting for retained objects, carrier JSON and graph lists.
    val estimatedBytes: Long get() = 4096L +
        network.subscriptions.sumOf { 1024L + it.cells.size * 4096L } +
        (diag.reading?.snapshot?.carriers?.size ?: 0) * 8192L +
        carriers.values.sumOf { it.size * 8192L } + diag.points.size * 64L
}

/** Replay needs points after the selected reading as well as its own past graph. */
internal fun replayThroughput(frames: List<NetworkReplayFrame>): List<ThputPoint> {
    val points = sortedMapOf<Long, ThputPoint>()
    for (frame in frames) {
        for (point in frame.diag.points) points[point.t] = point
    }
    return points.values.toList()
}

/** Foreground history only. Opening a timeline makes a stable copy for scrubbing. */
class ReplayHistory<T>(
    private val durationMillis: Long = 3_600_000,
    private val maxEntries: Int = 3600,
    private val maxBytes: Long = 24L * 1024 * 1024
) {
    private data class Entry<T>(val time: Long, val bytes: Long, val value: T)
    private val entries = ArrayDeque<Entry<T>>()
    private var retainedBytes = 0L

    fun append(elapsedMillis: Long, bytes: Long, value: T) {
        require(bytes >= 0)
        // Android and DIAG polls can complete in the same second. Store one round.
        val last = entries.lastOrNull()
        if (last != null && elapsedMillis / 1000 <= last.time / 1000) return
        entries.addLast(Entry(elapsedMillis, bytes, value))
        retainedBytes += bytes
        while (entries.isNotEmpty() && (entries.size > maxEntries || retainedBytes > maxBytes ||
                elapsedMillis - entries.first().time > durationMillis)) {
            retainedBytes -= entries.removeFirst().bytes
        }
    }

    fun open(): List<T> = entries.map { it.value }
}

/** Activity-owned polling and recording; pager pages only display the retained readings. */
class NetworkReplayModel(application: Application) : AndroidViewModel(application) {
    private val history = ReplayHistory<NetworkReplayFrame>()
    private val source = NetworkInfoSource(application)
    private val foreground = MutableStateFlow(false)
    private val network = MutableStateFlow<NetworkInfoSnapshot?>(null)
    private val mutableFrame = MutableStateFlow<NetworkReplayFrame?>(null)
    val liveFrame = mutableFrame.asStateFlow()
    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing = mutableRefreshing.asStateFlow()
    private val readLock = Mutex()
    private var monitor: DiagMonitor? = null
    private var previousOwner: Int? = null

    /** Bound once by the app shell, independently of which pager page is composed. */
    fun bind(diagMonitor: DiagMonitor) {
        if (monitor != null) return
        monitor = diagMonitor
        viewModelScope.launch {
            combine(network, diagMonitor.state, foreground) { snapshot, diag, active ->
                if (active && snapshot != null) makeFrame(snapshot, diag, diagMonitor) else null
            }.collect { frame ->
                if (frame != null) {
                    mutableFrame.value = frame
                    history.append(frame.elapsedMillis, frame.estimatedBytes, frame)
                }
            }
        }
        viewModelScope.launch {
            foreground.collectLatest { active ->
                if (!active) return@collectLatest
                while (isActive) {
                    readNetwork()
                    delay(1000)
                }
            }
        }
    }

    fun setForeground(active: Boolean) {
        foreground.value = active
        monitor?.setActive(active)
    }

    fun refresh() {
        if (!foreground.value || mutableRefreshing.value) return
        mutableRefreshing.value = true
        viewModelScope.launch {
            try {
                readNetwork()
            } finally {
                mutableRefreshing.value = false
            }
        }
    }

    private suspend fun readNetwork() = readLock.withLock {
        if (!foreground.value || !source.hasPhonePermission()) return@withLock
        val snapshot = withContext(Dispatchers.IO) {
            if (source.hasFineLocationPermission()) source.requestFreshCellInfo()
            source.readSnapshot()
        }
        if (foreground.value) network.value = snapshot
    }

    private fun makeFrame(
        snapshot: NetworkInfoSnapshot, diag: DiagMonitor.State, diagMonitor: DiagMonitor
    ): NetworkReplayFrame {
        val data = diag.reading?.snapshot
        val matched = data?.let { diagSubscription(it, snapshot.subscriptions) }
        if (!diag.enabled) previousOwner = null
        else if (matched != null) previousOwner = matched
        val owner = diagDisplayOwner(matched, previousOwner, snapshot.subscriptions)
        val carriers = snapshot.subscriptions.associate { subscription ->
            val id = subscription.subscriptionId
            id to if (diag.enabled && owner == id) {
                diagMonitor.holdPorts(id, diagDisplayCarriers(subscription, data.takeIf { matched == id }))
            } else emptyList()
        }
        return NetworkReplayFrame(SystemClock.elapsedRealtime(), snapshot, diag, matched, owner, carriers)
    }

    fun open(): List<NetworkReplayFrame> = history.open()
}
