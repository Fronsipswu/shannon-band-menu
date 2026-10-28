package fronsipswu.shannonbandmenu.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fronsipswu.shannonbandmenu.diag.DiagMonitor
import fronsipswu.shannonbandmenu.diag.DiagSnapshot
import fronsipswu.shannonbandmenu.diag.diagServingCell
import fronsipswu.shannonbandmenu.NetworkCell
import fronsipswu.shannonbandmenu.NetworkCellRole
import fronsipswu.shannonbandmenu.NetworkInfoSource
import fronsipswu.shannonbandmenu.NetworkSubscription
import fronsipswu.shannonbandmenu.R
import fronsipswu.shannonbandmenu.isNrNsa
import fronsipswu.shannonbandmenu.lteFrequenciesForEarfcn
import fronsipswu.shannonbandmenu.nrFrequencyForArfcn
import fronsipswu.shannonbandmenu.network.TrafficTest
import fronsipswu.shannonbandmenu.network.NetworkReplayFrame
import fronsipswu.shannonbandmenu.network.NetworkReplayModel
import fronsipswu.shannonbandmenu.network.replayThroughput
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    isActive: Boolean,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val source = remember(context) { NetworkInfoSource(context.applicationContext) }
    val trafficTest = remember(context) { TrafficTest.get(context) }
    val testConfig by trafficTest.config.collectAsState()
    val testRun by trafficTest.run.collectAsState()
    var showTestSetup by rememberSaveable { mutableStateOf(false) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var monitoringFrozen by rememberSaveable { mutableStateOf(false) }
    val activity = context as ComponentActivity
    val diagMonitor = remember(activity) { ViewModelProvider(activity)[DiagMonitor::class.java] }
    val diagState by diagMonitor.state.collectAsState()
    val replayModel = remember(activity) { ViewModelProvider(activity)[NetworkReplayModel::class.java] }
    val liveFrame by replayModel.liveFrame.collectAsState()
    val isRefreshing by replayModel.refreshing.collectAsState()
    var replayFrames by remember { mutableStateOf<List<NetworkReplayFrame>>(emptyList()) }
    var replayPosition by remember { mutableIntStateOf(0) }
    var replayPlaying by remember { mutableStateOf(false) }
    var pageVisible by remember { mutableStateOf(false) }

    fun toggleFrozen() {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        monitoringFrozen = !monitoringFrozen
    }

    fun toggleTrafficTest() {
        if (trafficTest.run.value.running) trafficTest.stop()
        else if (!trafficTest.start()) showTestSetup = true
    }
    val doubleTapAction by rememberUpdatedState { toggleFrozen() }
    val tripleTapAction by rememberUpdatedState {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        toggleTrafficTest()
    }

    fun extendReplay() {
        val selectedTime = replayFrames.getOrNull(replayPosition)?.elapsedMillis ?: return
        val fresh = replayModel.open()
        if (fresh.isEmpty()) return
        replayPosition = fresh.indexOfLast { it.elapsedMillis <= selectedTime }.coerceAtLeast(0)
        replayFrames = fresh
    }

    LaunchedEffect(monitoringFrozen) {
        replayPlaying = false
        replayFrames = if (monitoringFrozen) replayModel.open() else emptyList()
        replayPosition = (replayFrames.size - 1).coerceAtLeast(0)
    }
    LaunchedEffect(replayPlaying, pageVisible) {
        if (!pageVisible) replayPlaying = false
        while (replayPlaying && replayPosition < replayFrames.lastIndex) {
            val index = replayPosition
            delay((replayFrames[index + 1].elapsedMillis - replayFrames[index].elapsedMillis).coerceAtLeast(1))
            replayPosition = index + 1
        }
        replayPlaying = false
    }
    val shownFrame = if (monitoringFrozen) replayFrames.getOrNull(replayPosition) ?: liveFrame else liveFrame
    val snapshot = shownFrame?.network
    val shownDiag = shownFrame?.diag ?: diagState
    val displayedDiag = shownDiag.reading
    val displayedAtMillis = shownDiag.sampledAtMillis
    val timelinePoints = remember(replayFrames) { replayThroughput(replayFrames) }
    val displayedPoints = remember(monitoringFrozen, timelinePoints, displayedAtMillis, shownDiag.points) {
        if (monitoringFrozen && replayFrames.isNotEmpty()) timelinePoints.filter {
            it.t in displayedAtMillis - THPUT_BEFORE_MS..displayedAtMillis + THPUT_AFTER_MS
        } else shownDiag.points
    }
    val matchedDiagOwner = shownFrame?.matchedOwner

    DisposableEffect(activity, isActive) {
        fun updateActivity() {
            // Only replay playback depends on whether this page is selected.
            pageVisible = isActive && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> updateActivity() }
        activity.lifecycle.addObserver(observer)
        updateActivity()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            pageVisible = false
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRevision++
        replayModel.refresh()
    }

    val phonePermissionGranted = remember(permissionRevision) {
        source.hasPhonePermission()
    }
    val fineLocationGranted = remember(permissionRevision) {
        source.hasFineLocationPermission()
    }

    fun refresh() {
        if (monitoringFrozen || isRefreshing) return
        replayModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).statusBarsPadding()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refresh,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectNetworkTaps(onDoubleTap = { doubleTapAction() }, onTripleTap = { tripleTapAction() })
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!phonePermissionGranted || !fineLocationGranted) {
                    item {
                        PermissionCard(
                            phonePermissionGranted = phonePermissionGranted,
                            fineLocationGranted = fineLocationGranted,
                            onGrant = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        )
                    }
                }

                shownDiag.error?.let { message ->
                    item(key = "diag-error") { NoticeCard("DIAG stopped: $message", isError = true) }
                }
                val current = snapshot
                val diag = displayedDiag?.snapshot.takeIf { shownDiag.enabled }
                val diagOwner = shownFrame?.displayOwner

                if (phonePermissionGranted && current == null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (current != null) {
                    if (!current.locationEnabled) {
                        item {
                            NoticeCard(
                                "Location is turned off. Android may hide cell identities."
                            )
                        }
                    }
                    current.error?.let { message ->
                        item { NoticeCard(message, isError = true) }
                    }
                    if (current.subscriptions.isEmpty() && current.error == null) {
                        item { NoticeCard("No active cellular subscription was found.") }
                    }

                    current.subscriptions.forEach { subscription ->
                        val id = subscription.subscriptionId
                        val primaryCells = subscription.cells.filter {
                            it.role == NetworkCellRole.PRIMARY
                        }
                        // Layout follows the selected mode, never a transient sample's availability.
                        val usesDiag = shownDiag.enabled && diagOwner == id
                        val subscriptionDiag = diag.takeIf { matchedDiagOwner == id }
                        val diagCarriers = shownFrame.carriers[id].orEmpty()
                        val nrNsa = subscriptionDiag?.rat?.let { it == "NSA" } ?: isNrNsa(subscription.cells)
                        val heldDiag = subscriptionDiag?.copy(carriers = diagCarriers)
                        val diagPrimaries = primaryCells.map { diagServingCell(it, heldDiag) }


                        if (current.subscriptions.size > 1) {
                            item(key = "sim-title-$id") {
                                SectionTitle("SIM ${subscription.simSlot + 1}")
                            }
                        }
                        // The summary and the primary cell share one card; any
                        // further primary cell keeps its own card below.
                        item(key = "summary-$id") {
                            SummaryCard(
                                subscription, diagPrimaries.firstOrNull(), nrNsa,
                                frozen = monitoringFrozen.takeIf { subscription === current.subscriptions.first() },
                                onFrozenToggle = ::toggleFrozen,
                                diagEnabled = diagState.enabled,
                                onDiagToggle = { diagMonitor.setEnabled(!diagState.enabled) },
                                testRun = testRun,
                                onTestSetup = { showTestSetup = true },
                                onTestToggle = ::toggleTrafficTest,
                                diagStatus = if (subscription === current.subscriptions.first() && shownDiag.enabled) {
                                    when {
                                        shownDiag.starting -> "DIAG · Connecting"
                                        diagOwner == null -> "DIAG · Waiting for serving cell"
                                        displayedDiag?.transport == "broker" -> "DIAG (relay)"
                                        else -> "DIAG (Direct)"
                                    }
                                } else null
                            )
                        }
                        if (primaryCells.isEmpty()) {
                            item(key = "primary-empty-$id") {
                                NoticeCard("No primary serving cell is currently reported.")
                            }
                        }
                        primaryCells.drop(1).forEachIndexed { index, cell ->
                            item(key = cellKey(subscription, cell, "p${index + 1}")) {
                                CellSection(
                                    title = "Primary ${cell.technology}",
                                    cell = cell,
                                    showNrNsaMetrics = nrNsa
                                )
                            }
                        }

                        if (usesDiag) {
                            val lte = diagCarriers.filter { !it.nr }
                            val nr = diagCarriers.filter { it.nr }
                            if (lte.isNotEmpty()) item(key = "diag-lte-$id") {
                                DiagCarrierCard("Carrier aggregation", lte)
                            }
                            if (nr.isNotEmpty()) item(key = "diag-nr-$id") {
                                DiagCarrierCard("NR aggregation", nr)
                            }
                            subscriptionDiag?.wcdma?.let { measurements ->
                                item(key = "diag-wcdma-$id") { WcdmaDiagCard(measurements) }
                            }
                            item(key = "throughput-$id") {
                                DiagTitle("Throughput")
                                ThroughputCard(displayedPoints, displayedAtMillis)
                            }
                            val uplink = diagCarriers.filter { it.primary || it.hasUplink }
                            if (uplink.isNotEmpty()) item(key = "uplink-$id") {
                                DiagCarrierCard("Uplink", uplink, uplink = true)
                            }
                            if (diagCarriers.isNotEmpty()) item(key = "antennas-$id") {
                                DiagAntennaCard(diagCarriers)
                            }
                        } else {
                            carrierGroups(subscription, nrNsa).forEach { group ->
                                item(key = "carriers-$id-${group.title}") {
                                    CarrierSection(group, diagEnabled = shownDiag.enabled)
                                }
                            }
                        }
                        // Secondary cells of any other technology keep a card each.
                        subscription.cells
                            .filter {
                                it.role == NetworkCellRole.SECONDARY &&
                                    it.technology != "LTE" && it.technology != "NR"
                            }
                            .forEachIndexed { index, cell ->
                                item(key = cellKey(subscription, cell, "s$index")) {
                                    CellSection(
                                        title = "Secondary ${cell.technology}",
                                        cell = cell,
                                        showNrNsaMetrics = nrNsa
                                    )
                                }
                            }
                    }
                }
            }
        }
        if (monitoringFrozen && replayFrames.isNotEmpty()) {
            NetworkReplayBar(
                size = replayFrames.size,
                position = replayPosition,
                wallMillis = replayFrames[replayPosition].network.capturedAtMillis,
                playing = replayPlaying,
                onPosition = {
                    replayPlaying = false
                    replayPosition = it.coerceIn(0, replayFrames.lastIndex)
                },
                onPlay = {
                    if (!replayPlaying && replayPosition == replayFrames.lastIndex) replayPosition = 0
                    replayPlaying = !replayPlaying
                },
                onDragStart = {
                    replayPlaying = false
                    extendReplay()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
    TestScriptDialog(
        show = showTestSetup,
        config = testConfig,
        onDismiss = { showTestSetup = false },
        onSave = {
            trafficTest.save(it)
            showTestSetup = false
        }
    )
}

@Composable
private fun PermissionCard(
    phonePermissionGranted: Boolean,
    fineLocationGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Telephony access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal
            )
            Text(
                when {
                    !phonePermissionGranted && !fineLocationGranted ->
                        "Phone state provides service information. Precise location allows Android to expose serving-cell identities."
                    !phonePermissionGranted ->
                        "Phone-state permission is needed to read active SIM and service information."
                    else ->
                        "Precise location is needed for PCI, cell ID, channel, and secondary serving cells."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text("Grant access")
            }
        }
    }
}

@Composable
private fun NoticeCard(message: String, isError: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Small grey heading that starts at the cards' inner text edge. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------------------------------------------------------------------------
// Summary card: mode pill, status chips, the primary cell's bars and fields.
// ---------------------------------------------------------------------------

/** Value text: pure white on the dark theme (black on the light one, so it stays readable). */
@Composable
private fun valueTextColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black

private data class Field(val label: String, val value: String)

private data class BarMetric(
    val label: String,
    val metric: SignalMetric,
    val value: Int?,
    val unit: String
)

@Composable
private fun SummaryCard(
    subscription: NetworkSubscription,
    primary: NetworkCell?,
    nrNsa: Boolean,
    /** The padlock at the card's top right; null leaves it out. */
    frozen: Boolean? = null,
    onFrozenToggle: () -> Unit = {},
    diagEnabled: Boolean = false,
    onDiagToggle: () -> Unit = {},
    testRun: TrafficTest.Run = TrafficTest.Run(),
    onTestSetup: () -> Unit = {},
    onTestToggle: () -> Unit = {},
    diagStatus: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val inService = subscription.dataNetwork != "Unknown"
                Row(
                    Modifier.fillMaxWidth().padding(end = if (frozen != null) 128.dp else 0.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (inService) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            subscription.dataNetwork,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (inService) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip("Voice: ${subscription.voiceNetwork}")
                        Chip(subscription.dataState, highlight = subscription.dataState == "Connected")
                    }
                    diagStatus?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                if (frozen != null) TrafficTestStatus(testRun)
                // Operator, MCC / MNC and roaming: the top row of fields.
                val operatorRow = listOf(
                    Field("Operator", subscription.operatorName.ifBlank { "Unknown" }),
                    Field(
                        "MCC / MNC",
                        listOfNotNull(subscription.mcc, subscription.mnc)
                            .joinToString(" / ").ifBlank { "Unknown" }
                    ),
                    Field("Roaming", if (subscription.roaming) "Yes" else "No")
                )
                // Primary cell: its bars, then its fields.
                val metrics = primary?.let { cellMetrics(it, nrNsa) }.orEmpty()
                if (metrics.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    metrics.forEach { SignalBar(it, Modifier.padding(vertical = 3.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                FieldGrid(primary?.let(::cellFields).orEmpty(), extraRow = operatorRow, compact = true)
            }
            if (frozen != null) {
                Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp)) {
                    TrafficTestButton(testRun, onTestToggle)
                    IconButton(onClick = onFrozenToggle) {
                        Icon(
                            painter = painterResource(
                                if (frozen) R.drawable.ic_monitoring_locked
                                else R.drawable.ic_monitoring_unlocked
                            ),
                            contentDescription = if (frozen) "Unfreeze live monitoring" else "Freeze live monitoring",
                            tint = if (frozen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DiagOverflowMenu(diagEnabled, onDiagToggle, onTestSetup)
                }
            }
        }
    }
}

@Composable
private fun DiagOverflowMenu(enabled: Boolean, onToggle: () -> Unit, onTestSetup: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showRelayTimer by remember { mutableStateOf(false) }
    if (showRelayTimer) RelayIdleTimerDialog { showRelayTimer = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Network options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Relay idle timer") },
                onClick = { expanded = false; showRelayTimer = true }
            )
            DropdownMenuItem(
                text = { Text("Enable DIAG") },
                trailingIcon = { if (enabled) Icon(Icons.Outlined.Check, contentDescription = null) },
                onClick = { expanded = false; onToggle() }
            )
            DropdownMenuItem(
                text = { Text("Test script") },
                onClick = {
                    expanded = false
                    onTestSetup()
                }
            )
        }
    }
}

@Composable
private fun SignalBar(metric: BarMetric, modifier: Modifier = Modifier) =
    SignalBar(
        label = metric.label,
        value = metric.value,
        metric = metric.metric,
        unit = metric.unit,
        modifier = modifier
    )

@Composable
private fun Chip(text: String, highlight: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            )
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CellSection(title: String, cell: NetworkCell, showNrNsaMetrics: Boolean) {
    SectionTitle(title)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val metrics = cellMetrics(cell, showNrNsaMetrics)
            metrics.forEach { SignalBar(it, Modifier.padding(vertical = 3.dp)) }
            if (metrics.isNotEmpty()) Spacer(Modifier.height(12.dp))
            FieldGrid(cellFields(cell))
        }
    }
}

@Composable
private fun WcdmaDiagCard(data: fronsipswu.shannonbandmenu.diag.WcdmaSnapshot) {
    fun value(number: Double?, unit: String = "") = number?.let {
        String.format(java.util.Locale.US, "%.1f%s", it, unit)
    } ?: "—"
    SectionTitle("HSPA DIAG")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            FieldGrid(listOf(
                Field("PSC", data.psc?.toString() ?: "—"),
                Field("CPICH RSCP", value(data.rscp, " dBm")),
                Field("CPICH Ec/No", value(data.ecno, " dB")),
                Field("CQI (weighted)", value(data.cqi)),
                Field("HS-SCCH success", value(data.scchSuccess, "%")),
                Field("QPSK usage", value(data.qpsk, "%")),
                Field("16QAM usage", value(data.qam16, "%")),
                Field("64QAM usage", value(data.qam64, "%"))
            ))
        }
    }
}

/**
 * Compact three-column label/value grid. A field spans 1, 2 or 3 columns by
 * value length (ECI, DL/UL and the like need more), packed in order. An
 * [extraRow] is packed on its own and goes before the cell's fields.
 */
@Composable
private fun FieldGrid(fields: List<Field>, extraRow: List<Field> = emptyList(), compact: Boolean = false) {
    BoxWithConstraints {
        fun span(f: Field) = when {
            f.value.length <= 11 -> 1
            f.value.length <= 24 -> 2
            else -> 3
        }

        fun pack(list: List<Field>) = buildList<List<Field>> {
            var row = mutableListOf<Field>()
            var used = 0
            for (f in list) {
                if (used + span(f) > 3) {
                    add(row)
                    row = mutableListOf()
                    used = 0
                }
                row += f
                used += span(f)
            }
            if (row.isNotEmpty()) add(row)
        }

        val rows = pack(extraRow) + pack(fields)
        // The third column is narrower, which moves the second and third
        // columns right so the right side looks less empty.
        val columns = listOf(1.08f, 1.08f, 0.84f)
        val totalWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall.let {
            if (compact) it.copy(fontSize = 10.sp, lineHeight = 14.sp)
            else it.copy(fontSize = it.fontSize * 0.92f)
        }
        val baseValue = MaterialTheme.typography.bodyMedium.let {
            if (compact) it.copy(fontSize = 12.sp, lineHeight = 16.sp)
            else it.copy(fontSize = it.fontSize * 0.92f)
        }
        Column {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    var col = 0
                    row.forEach { f ->
                        val weight = columns.subList(col, col + span(f)).sum()
                        col += span(f)
                        val colWidthPx = totalWidthPx * (weight / 3f)
                        val textWidth = remember(f.value, baseValue) {
                            measurer.measure(f.value, baseValue).size.width
                        }
                        val fit = if (textWidth > 0 && colWidthPx > 0f) (colWidthPx - 1f) / textWidth else 1f
                        val valueStyle =
                            if (fit >= 1f) baseValue else baseValue.copy(fontSize = baseValue.fontSize * fit)
                        Column(Modifier.weight(weight)) {
                            Text(
                                f.label,
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            // The Band value takes its band's display colour, as in the tables.
                            val tint = if (f.label == "Band") bandColor(f.value) else null
                            Text(
                                f.value,
                                style = valueStyle,
                                color = tint ?: valueTextColor(),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    val free = columns.drop(col).sum()
                    if (free > 0f) Spacer(Modifier.weight(free))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Carrier tables: one column per carrier, one row per parameter.
// ---------------------------------------------------------------------------

private data class CarrierColumn(val role: String, val cell: NetworkCell)

private data class CarrierGroup(
    val title: String,
    val columns: List<CarrierColumn>,
    val nr: Boolean,
    /** NR NSA: Telephony reports only the core identity rows for these cells. */
    val coreOnly: Boolean,
    val showNrNsaMetrics: Boolean
)

/**
 * The LTE and NR tables of a subscription: the primary cell first (P), then
 * its secondary cells (S1, S2, ...).
 */
private fun carrierGroups(subscription: NetworkSubscription, nrNsa: Boolean): List<CarrierGroup> {
    fun columnsOf(technology: String): List<CarrierColumn> {
        val ofTechnology = subscription.cells.filter { it.technology == technology }
        val primaries = ofTechnology.filter { it.role == NetworkCellRole.PRIMARY }
        val secondaries = ofTechnology.filter { it.role == NetworkCellRole.SECONDARY }
        return primaries.map { CarrierColumn("P", it) } +
            secondaries.mapIndexed { index, cell -> CarrierColumn("S${index + 1}", cell) }
    }

    return buildList {
        val lte = columnsOf("LTE")
        if (lte.isNotEmpty()) {
            add(CarrierGroup("Carrier aggregation", lte, nr = false, coreOnly = false, showNrNsaMetrics = nrNsa))
        }
        val nr = columnsOf("NR")
        if (nr.isNotEmpty()) {
            add(CarrierGroup("NR aggregation", nr, nr = true, coreOnly = nrNsa, showNrNsaMetrics = nrNsa))
        }
    }
}

@Composable
private fun CarrierSection(group: CarrierGroup, diagEnabled: Boolean = false) {
    SectionTitle(group.title)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            CarrierTable(group, diagEnabled)
        }
    }
}

private val TABLE_LABEL_WIDTH = 60.dp

/** Carriers shown side by side before the columns share the width. */
private const val TABLE_FIXED_COLUMNS = 5

/** One column per carrier, one row per parameter. */
@Composable
private fun CarrierTable(group: CarrierGroup, diagEnabled: Boolean = false) {
    val columns = group.columns
    BoxWithConstraints {
        // Up to five carriers each column is fixed at the width five columns
        // share, and the columns sit from the left, so a lone PCell isn't
        // centred in empty space. From six carriers the columns share the
        // width, which shrinks them further.
        val colWidth = (maxWidth - TABLE_LABEL_WIDTH) / TABLE_FIXED_COLUMNS
        val fixedCols = columns.size <= TABLE_FIXED_COLUMNS
        val barMax = colWidth - 4.dp
        val cellStyle = fittedCellStyle(
            columns,
            if (fixedCols) colWidth else (maxWidth - TABLE_LABEL_WIDTH) / columns.size
        )
        val grey = MaterialTheme.colorScheme.onSurfaceVariant
        val fg = valueTextColor()
        val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

        @Composable
        fun row(label: String, header: Boolean = false, cell: @Composable (CarrierColumn) -> Unit) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .drawBehind {
                        drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                    }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    modifier = Modifier.width(TABLE_LABEL_WIDTH),
                    style = MaterialTheme.typography.labelMedium,
                    color = grey,
                    fontWeight = if (header) FontWeight.Medium else null,
                    maxLines = 1,
                    softWrap = false
                )
                columns.forEach { column ->
                    Box(
                        (if (fixedCols) Modifier.width(colWidth) else Modifier.weight(1f))
                            .padding(horizontal = 2.dp),
                        Alignment.Center
                    ) { cell(column) }
                }
            }
        }

        @Composable
        fun text(value: String?, bold: Boolean = false, color: Color = fg) =
            Text(
                value ?: "–",
                style = cellStyle,
                fontWeight = if (bold) FontWeight.SemiBold else null,
                color = if (value == null) grey else color,
                maxLines = 1,
                softWrap = false
            )

        @Composable
        fun bar(value: Int?, metric: SignalMetric) = CellValueBar(value, metric, barMax, cellStyle)

        fun anyHas(value: (NetworkCell) -> Any?) = columns.any { value(it.cell) != null }

        Column {
            // The primary cell's role is blue; the secondaries stay grey.
            row("", header = true) {
                text(it.role, color = if (it.role == "P") MaterialTheme.colorScheme.primary else grey)
            }
            if (group.nr) {
                if (!group.coreOnly) {
                    row("Band") {
                        val band = it.cell.band?.let { b -> "n$b" }
                        text(band, bold = true, color = bandColor(band) ?: fg)
                    }
                }
                row("BW") { text(it.cell.bandwidthKhz?.let { bw -> formatDecimal(bw / 1000.0) }) }
                row("NARFCN") { text(it.cell.channel?.toString()) }
                if (anyHas { c -> c.channel?.let(::nrFrequencyForArfcn) }) {
                    row("Freq") {
                        text(it.cell.channel?.let(::nrFrequencyForArfcn)?.let(::formatDecimal))
                    }
                }
                row("PCI") { text(it.cell.physicalId?.toString()) }
                if (!group.coreOnly) {
                    row("RSRP") { bar(it.cell.rsrp, SignalMetric.RSRP) }
                    row("RSRQ") { bar(it.cell.rsrq, SignalMetric.RSRQ) }
                    if (group.showNrNsaMetrics) row("SINR") { bar(it.cell.sinr, SignalMetric.SINR) }
                }
            } else {
                row("Band") {
                    val band = it.cell.band?.let { b -> "B$b" }
                    text(band, bold = true, color = bandColor(band) ?: fg)
                }
                row("BW") { text(it.cell.bandwidthKhz?.let { bw -> formatDecimal(bw / 1000.0) }) }
                row("EARFCN") { text(it.cell.channel?.toString()) }
                if (!diagEnabled && anyHas { c -> c.channel?.let(::lteFrequenciesForEarfcn) }) {
                    row("DL MHz") {
                        text(
                            it.cell.channel?.let(::lteFrequenciesForEarfcn)
                                ?.let { f -> formatDecimal(f.downlinkMhz) }
                        )
                    }
                    row("UL MHz") {
                        text(
                            it.cell.channel?.let(::lteFrequenciesForEarfcn)
                                ?.uplinkMhz?.let(::formatDecimal)
                        )
                    }
                }
                row("PCI") { text(it.cell.physicalId?.toString()) }
                row("RSRP") { bar(it.cell.rsrp, SignalMetric.RSRP) }
                row("RSRQ") { bar(it.cell.rsrq, SignalMetric.RSRQ) }
                row("SINR") { bar(it.cell.sinr, SignalMetric.SINR) }
                if (anyHas { c -> c.rssi }) row("RSSI") { text(it.cell.rssi?.toString()) }
                if (anyHas { c -> c.cqi }) row("CQI") { text(it.cell.cqi?.toString()) }
            }
        }
    }
}

/**
 * The table's cell text style: bodyMedium with equal-width digits, scaled
 * down when a column (minus its padding) is narrower than the widest value
 * would be at the normal size, instead of wrapping onto two lines. The widest
 * candidates are a signal value and a channel of the longest length; they are
 * measured as templates so the size doesn't change with the digits shown.
 */
@Composable
private fun fittedCellStyle(columns: List<CarrierColumn>, columnWidth: Dp): TextStyle {
    val base = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val available = with(LocalDensity.current) { (columnWidth - 4.dp).toPx() }
    val digits = maxOf(6, columns.maxOfOrNull { it.cell.channel?.toString()?.length ?: 0 } ?: 0)
    val widest = remember(digits, base) {
        listOf("-140", "8".repeat(digits)).maxOf { measurer.measure(it, base).size.width }
    }
    val fit = if (widest > 0) available / widest else 1f
    return if (fit >= 1f) base else base.copy(fontSize = base.fontSize * fit)
}

// ---------------------------------------------------------------------------
// Cell content: the bars and fields of one cell.
// ---------------------------------------------------------------------------

private fun cellMetrics(cell: NetworkCell, showNrNsaMetrics: Boolean): List<BarMetric> =
    when (cell.technology) {
        "LTE" -> listOf(
            BarMetric("RSRP", SignalMetric.RSRP, cell.rsrp, "dBm"),
            BarMetric("RSRQ", SignalMetric.RSRQ, cell.rsrq, "dB"),
            BarMetric("SINR", SignalMetric.SINR, cell.sinr, "dB")
        )
        "NR" -> buildList {
            add(BarMetric("SS-RSRP", SignalMetric.RSRP, cell.rsrp, "dBm"))
            add(BarMetric("SS-RSRQ", SignalMetric.RSRQ, cell.rsrq, "dB"))
            if (showNrNsaMetrics) add(BarMetric("SS-SINR", SignalMetric.SINR, cell.sinr, "dB"))
        }
        "WCDMA" -> listOf(
            BarMetric("RSCP", SignalMetric.RSCP, cell.rsrp, "dBm"),
            BarMetric("EC/No", SignalMetric.ECNO, cell.sinr, "dB")
        )
        else -> emptyList()
    }

private fun cellFields(cell: NetworkCell): List<Field> = buildList {
    when (cell.technology) {
        "LTE" -> addLteFields(cell)
        "NR" -> addNrFields(cell)
        else -> addLegacyFields(cell)
    }
}

private fun MutableList<Field>.addLteFields(cell: NetworkCell) {
    cell.physicalId?.let { add(Field("PCI", it.toString())) }
    cell.cellId?.let { ci -> add(Field("eNB / Cell", "${ci shr 8}/${ci and 0xff}")) }
    cell.channel?.let { add(Field("EARFCN", it.toString())) }
    cell.band?.let { add(Field("Band", "B$it")) }
    cell.bandwidthKhz?.let { add(Field("Bandwidth", formatBandwidth(it))) }
    cell.rssi?.let { add(Field("RSSI", "$it dBm")) }
    if (cell.role == NetworkCellRole.PRIMARY) {
        cell.timingAdvance?.let { add(Field("Timing advance", it.toString())) }
    }
}

private fun MutableList<Field>.addNrFields(cell: NetworkCell) {
    cell.physicalId?.let { add(Field("PCI", it.toString())) }
    if (cell.role == NetworkCellRole.PRIMARY) {
        cell.cellId?.let { add(Field("NCI", it.toString())) }
    }
    cell.channel?.let { arfcn ->
        add(Field("NR-ARFCN", arfcn.toString()))
        nrFrequencyForArfcn(arfcn)?.let { add(Field("Frequency", "${formatDecimal(it)} MHz")) }
    }
    val bandwidth = cell.bandwidthKhz?.let { Field("Bandwidth", formatBandwidth(it)) }
    val band = cell.band?.let { Field("Band", "n$it") }
    if (cell.role == NetworkCellRole.SECONDARY) {
        bandwidth?.let(::add)
        band?.let(::add)
    } else {
        band?.let(::add)
        bandwidth?.let(::add)
    }
}

private fun MutableList<Field>.addLegacyFields(cell: NetworkCell) {
    cell.physicalId?.let {
        val label = when (cell.technology) {
            "WCDMA" -> "PSC"
            "GSM" -> "BSIC"
            "TD-SCDMA" -> "CPID"
            else -> "Physical ID"
        }
        add(Field(label, it.toString()))
    }
    cell.cellId?.let { add(Field("Cell ID", it.toString())) }
    cell.channel?.let {
        val label = when (cell.technology) {
            "WCDMA", "TD-SCDMA" -> "UARFCN"
            "GSM" -> "ARFCN"
            else -> "Channel"
        }
        add(Field(label, it.toString()))
    }
    when (cell.technology) {
        "TD-SCDMA" -> cell.rsrp?.let { add(Field("RSCP", "$it dBm")) }
        "GSM" -> {
            cell.rssi?.let { add(Field("RSSI", "$it dBm")) }
            cell.timingAdvance?.let { add(Field("Timing advance", it.toString())) }
        }
    }
}

private fun cellKey(
    subscription: NetworkSubscription,
    cell: NetworkCell,
    suffix: String
): String = listOf(
    subscription.subscriptionId,
    cell.technology,
    cell.physicalId,
    cell.channel,
    suffix
).joinToString("-")

private fun formatBandwidth(bandwidthKhz: Int): String {
    val mhz = bandwidthKhz / 1000.0
    return "${formatDecimal(mhz)} MHz"
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)
