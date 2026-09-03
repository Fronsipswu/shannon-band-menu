package fronsipswu.shannonbandmenu.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.NetworkCell
import fronsipswu.shannonbandmenu.NetworkCellRole
import fronsipswu.shannonbandmenu.NetworkInfoSnapshot
import fronsipswu.shannonbandmenu.NetworkInfoSource
import fronsipswu.shannonbandmenu.NetworkSubscription
import fronsipswu.shannonbandmenu.R
import fronsipswu.shannonbandmenu.bandwidthLabelForTechnology
import fronsipswu.shannonbandmenu.carrierAggregationLabel
import fronsipswu.shannonbandmenu.isNrNsa
import fronsipswu.shannonbandmenu.lteBandwidthsForNsa
import fronsipswu.shannonbandmenu.nrSaCarrierAggregationLabel
import fronsipswu.shannonbandmenu.totalBandwidthLabel
import fronsipswu.shannonbandmenu.lteFrequenciesForEarfcn
import fronsipswu.shannonbandmenu.lteTimingAdvanceMeters
import fronsipswu.shannonbandmenu.nrFrequencyForArfcn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    isActive: Boolean,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val source = remember(context) { NetworkInfoSource(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var permissionRevision by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<NetworkInfoSnapshot?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var monitoringFrozen by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRevision++
    }

    val phonePermissionGranted = remember(permissionRevision) {
        source.hasPhonePermission()
    }
    val fineLocationGranted = remember(permissionRevision) {
        source.hasFineLocationPermission()
    }

    suspend fun readSnapshot(): NetworkInfoSnapshot =
        withContext(Dispatchers.IO) { source.readSnapshot() }

    fun refresh() {
        if (monitoringFrozen || isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                if (fineLocationGranted) {
                    withContext(Dispatchers.IO) { source.requestFreshCellInfo() }
                }
                val refreshed = readSnapshot()
                if (!monitoringFrozen) snapshot = refreshed
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(
        isActive,
        monitoringFrozen,
        phonePermissionGranted,
        fineLocationGranted,
        permissionRevision
    ) {
        if (!isActive || monitoringFrozen || !phonePermissionGranted) return@LaunchedEffect
        while (isActive) {
            if (fineLocationGranted) {
                withContext(Dispatchers.IO) { source.requestFreshCellInfo() }
            }
            snapshot = readSnapshot()
            delay(1_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("Network Info") },
            actions = {
                IconButton(onClick = { monitoringFrozen = !monitoringFrozen }) {
                    Icon(
                        painter = painterResource(
                            if (monitoringFrozen) R.drawable.ic_monitoring_locked
                            else R.drawable.ic_monitoring_unlocked
                        ),
                        contentDescription = if (monitoringFrozen) {
                            "Unfreeze live monitoring"
                        } else {
                            "Freeze live monitoring"
                        }
                    )
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                val current = snapshot
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
                        item(key = "operator-${subscription.subscriptionId}") {
                            OperatorSection(subscription, current.subscriptions.size > 1)
                        }
                        item(key = "services-${subscription.subscriptionId}") {
                            ServicesSection(subscription)
                        }

                        val primaryCells = subscription.cells.filter {
                            it.role == NetworkCellRole.PRIMARY
                        }
                        val secondaryCells = subscription.cells.filter {
                            it.role == NetworkCellRole.SECONDARY
                        }
                        val nrNsa = isNrNsa(subscription.cells)
                        item(key = "registered-title-${subscription.subscriptionId}") {
                            SectionTitle("Primary cell")
                        }
                        if (primaryCells.isEmpty()) {
                            item(key = "registered-empty-${subscription.subscriptionId}") {
                                NoticeCard("No primary serving cell is currently reported.")
                            }
                        }
                        itemsIndexed(
                            primaryCells,
                            key = { index, cell -> cellKey(subscription, cell, "p$index") }
                        ) { _, cell ->
                            CellCard(cell, title = "Primary ${cell.technology} · SIM ${cell.simSlot + 1}")
                        }

                        if (secondaryCells.isNotEmpty()) {
                            if (nrNsa) {
                                val secondaryNr = secondaryCells.filter { it.technology == "NR" }
                                val secondaryLte = secondaryCells.filter { it.technology == "LTE" }
                                listOf(
                                    "NR" to secondaryNr,
                                    "LTE" to secondaryLte
                                ).forEach { (technology, cells) ->
                                    if (cells.isNotEmpty()) {
                                        item(
                                            key = "secondary-title-${subscription.subscriptionId}-$technology"
                                        ) {
                                            SectionTitle(
                                                "Secondary cell $technology (${cells.size})"
                                            )
                                        }
                                        item(
                                            key = "secondary-cells-${subscription.subscriptionId}-$technology"
                                        ) {
                                            // Telephony preserves Shannon/NSG SCell ordering.
                                            DetailsCard(
                                                rows = secondaryCellRows(
                                                    cells,
                                                    includeCellLabels = technology != "NR"
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                item(key = "secondary-title-${subscription.subscriptionId}") {
                                    SectionTitle("Secondary cells (${secondaryCells.size})")
                                }
                                item(key = "secondary-cells-${subscription.subscriptionId}") {
                                    // Telephony preserves Shannon/NSG SCell ordering.
                                    DetailsCard(rows = secondaryCellRows(secondaryCells))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun OperatorSection(subscription: NetworkSubscription, showSim: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(if (showSim) "Operator · SIM ${subscription.simSlot + 1}" else "Operator")
        DetailsCard(
            rows = listOf(
                "Name" to subscription.operatorName.ifBlank { "Unknown" },
                "MCC / MNC" to listOfNotNull(subscription.mcc, subscription.mnc)
                    .joinToString(" / ").ifBlank { "Unknown" },
                "Country" to subscription.countryIso.uppercase().ifBlank { "Unknown" },
                "Roaming" to if (subscription.roaming) "Yes" else "No"
            )
        )
    }
}

@Composable
private fun ServicesSection(subscription: NetworkSubscription) {
    val nrNsa = subscription.dataNetwork == "NR-NSA"
    val rows = buildList {
        add("Voice network" to subscription.voiceNetwork)
        add("Data network" to subscription.dataNetwork)
        val isNrSa = subscription.dataNetwork == "NR-SA" || subscription.dataNetwork == "NR" ||
            (subscription.voiceNetwork == "NR-SA" && subscription.dataNetwork != "LTE")
        if (nrNsa) {
            val lteCells = subscription.cells.filter { it.technology == "LTE" }
            val lteBandwidths = lteBandwidthsForNsa(subscription.cells, subscription.bandwidthsKhz)
            add("LTE aggregation" to carrierAggregationLabel(lteCells, lteBandwidths))
            totalBandwidthLabel(lteCells, lteBandwidths)?.let {
                add("LTE Bandwidth" to it)
            }
            add(
                "NR Bandwidth" to bandwidthLabelForTechnology(
                    "NR",
                    subscription.cells,
                    subscription.bandwidthsKhz
                )
            )
        } else if (isNrSa) {
            add(
                "Carrier aggregation" to nrSaCarrierAggregationLabel(
                    subscription.cells,
                    subscription.bandwidthsKhz
                )
            )
        } else if (subscription.dataNetwork == "LTE" ||
            subscription.cells.any { it.role == NetworkCellRole.PRIMARY && it.technology == "LTE" }
        ) {
            add(
                "Carrier aggregation" to carrierAggregationLabel(
                    subscription.cells,
                    subscription.bandwidthsKhz
                )
            )
            totalBandwidthLabel(
                subscription.cells,
                subscription.bandwidthsKhz
            )?.let { add("Total bandwidth" to it) }
        }
        add("Data state" to subscription.dataState)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("Services")
        DetailsCard(
            rows = rows,
            highlightedLabels = setOf("Voice network", "Data network")
        )
    }
}

@Composable
private fun DetailsCard(
    rows: List<Pair<String, String>>,
    highlightedLabels: Set<String> = emptySet(),
    title: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
            }
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "$label:",
                        modifier = Modifier.weight(0.46f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        value,
                        modifier = Modifier.weight(0.54f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (label in highlightedLabels) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun CellCard(
    cell: NetworkCell,
    title: String
) {
    DetailsCard(rows = cellRows(cell), title = title)
}

private fun cellRows(cell: NetworkCell): List<Pair<String, String>> = buildList {
    when (cell.technology) {
        "LTE" -> addLteRows(cell)
        "NR" -> addNrRows(cell)
        else -> addLegacyRows(cell)
    }
}

private fun secondaryCellRows(
    cells: List<NetworkCell>,
    includeCellLabels: Boolean = true
): List<Pair<String, String>> {
    val rowsByCell = cells.map(::cellRows)
    val labels = rowsByCell.flatMap { rows -> rows.map { it.first } }.distinct()

    return labels.mapNotNull { label ->
        val values = rowsByCell.mapIndexedNotNull { index, rows ->
            rows.firstOrNull { it.first == label }
                ?.second
                ?.let { value ->
                    if (includeCellLabels) "$value (S${index + 1})" else value
                }
        }
        values.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let { label to it }
    }
}

private fun MutableList<Pair<String, String>>.addLteRows(cell: NetworkCell) {
    cell.physicalId?.let { add("PCI" to it.toString()) }
    cell.cellId?.let { ci ->
        add("ECI" to "$ci (eNB ${ci shr 8}, cell ${ci and 0xff})")
    }
    cell.channel?.let { add("EARFCN" to it.toString()) }
    cell.band?.let { add("Band" to "B$it") }
    cell.channel?.let { earfcn ->
        lteFrequenciesForEarfcn(earfcn)?.let { frequencies ->
            val downlink = formatDecimal(frequencies.downlinkMhz)
            val uplink = frequencies.uplinkMhz?.let(::formatDecimal) ?: "—"
            add("Downlink/Uplink" to "$downlink / $uplink MHz")
        }
    }
    cell.bandwidthKhz?.let { add("Bandwidth" to formatBandwidth(it)) }
    cell.rsrp?.let { add("RSRP" to "$it dBm") }
    cell.rsrq?.let { add("RSRQ" to "$it dB") }
    cell.sinr?.let { add("RSSNR" to "$it dB") }
    cell.rssi?.let { add("RSSI" to "$it dBm") }
    cell.cqi?.let { add("CQI" to it.toString()) }
    if (cell.role == NetworkCellRole.PRIMARY) {
        cell.timingAdvance?.let { timingAdvance ->
            add("Timing advance" to timingAdvance.toString())
            lteTimingAdvanceMeters(timingAdvance)?.let { distance ->
                add(
                    "Estimated distance" to
                        "${formatDecimal(distance)} m / ${formatDecimal(distance * 3.28084)} ft"
                )
            }
        }
    }
}

private fun MutableList<Pair<String, String>>.addNrRows(cell: NetworkCell) {
    cell.physicalId?.let { add("PCI" to it.toString()) }
    if (cell.role == NetworkCellRole.PRIMARY) {
        cell.cellId?.let { add("NCI" to it.toString()) }
    }
    cell.channel?.let { arfcn ->
        add("NR-ARFCN" to arfcn.toString())
        nrFrequencyForArfcn(arfcn)?.let { add("Frequency" to "${formatDecimal(it)} MHz") }
    }
    cell.band?.let { add("Band" to "n$it") }
    cell.rsrp?.let { add("SS-RSRP" to "$it dBm") }
    cell.rsrq?.let { add("SS-RSRQ" to "$it dB") }
    cell.sinr?.let { add("SS-SINR" to "$it dB") }
    cell.nrCsiRsrp?.let { add("CSI-RSRP" to "$it dBm") }
    cell.nrCsiRsrq?.let { add("CSI-RSRQ" to "$it dB") }
    cell.nrCsiSinr?.let { add("CSI-SINR" to "$it dB") }
    if (cell.role == NetworkCellRole.PRIMARY) {
        cell.timingAdvance?.let { add("Timing advance" to "$it µs") }
    }
}

private fun MutableList<Pair<String, String>>.addLegacyRows(cell: NetworkCell) {
    cell.physicalId?.let {
        val label = when (cell.technology) {
            "WCDMA" -> "PSC"
            "GSM" -> "BSIC"
            "TD-SCDMA" -> "CPID"
            else -> "Physical ID"
        }
        add(label to it.toString())
    }
    cell.cellId?.let { add("Cell ID" to it.toString()) }
    cell.channel?.let {
        val label = when (cell.technology) {
            "WCDMA", "TD-SCDMA" -> "UARFCN"
            "GSM" -> "ARFCN"
            else -> "Channel"
        }
        add(label to it.toString())
    }
    when (cell.technology) {
        "WCDMA" -> {
            cell.rsrp?.let { add("RSCP" to "$it dBm") }
            cell.sinr?.let { add("Ec/No" to "$it dB") }
        }
        "TD-SCDMA" -> cell.rsrp?.let { add("RSCP" to "$it dBm") }
        "GSM" -> {
            cell.rssi?.let { add("RSSI" to "$it dBm") }
            cell.timingAdvance?.let { add("Timing advance" to it.toString()) }
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
