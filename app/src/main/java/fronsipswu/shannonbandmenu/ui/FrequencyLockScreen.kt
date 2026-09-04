package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.FrequencyLockState

private data class ParsedFrequencyLock(
    val lteEarfcns: List<Int>,
    val ltePci: Int?,
    val nrArfcn: Int?,
    val nrPci: Int?
)

private fun parseFrequencyLock(
    lteEarfcnsText: String,
    ltePciText: String,
    nrArfcnText: String,
    nrPciText: String
): Result<ParsedFrequencyLock> = runCatching {
    val earfcns = if (lteEarfcnsText.isBlank()) {
        emptyList()
    } else {
        val parts = lteEarfcnsText.split(',').map(String::trim)
        require(parts.none(String::isEmpty)) { "Separate LTE EARFCNs with a single comma" }
        require(parts.size <= 10) { "LTE supports at most 10 EARFCNs" }
        val parsed = parts.map {
            val value = it.toIntOrNull() ?: error("LTE EARFCNs must be whole numbers")
            require(value in 1..262143) { "LTE EARFCN must be between 1 and 262143" }
            value
        }
        require(parsed.distinct().size == parsed.size) { "Remove duplicate LTE EARFCNs" }
        parsed
    }

    fun optionalValue(text: String, label: String, range: IntRange): Int? {
        if (text.isBlank()) return null
        val value = text.trim().toIntOrNull() ?: error("$label must be a whole number")
        require(value in range) { "$label must be between ${range.first} and ${range.last}" }
        return value
    }

    ParsedFrequencyLock(
        lteEarfcns = earfcns,
        ltePci = optionalValue(ltePciText, "LTE PCI", 0..503),
        nrArfcn = optionalValue(nrArfcnText, "NR-ARFCN", 1..3279165),
        nrPci = optionalValue(nrPciText, "NR PCI", 0..1007)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrequencyLockScreen(
    state: FrequencyLockState,
    isRefreshing: Boolean,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onApply: (List<Int>, Int?, Int?, Int?) -> Unit,
    onReset: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    var lteEarfcns by remember { mutableStateOf("") }
    var ltePci by remember { mutableStateOf("") }
    var nrArfcn by remember { mutableStateOf("") }
    var nrPci by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, state) {
        if (state.valid) {
            lteEarfcns = state.lteEarfcnList.joinToString(",")
            ltePci = state.ltePci?.toString().orEmpty()
            nrArfcn = state.nrArfcn?.toString().orEmpty()
            nrPci = state.nrPci?.toString().orEmpty()
            validationError = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(title = { Text("Cell Lock") })

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Leave any field empty then apply to clear that specific lock. Applying or resetting will perform a full modem reboot, which will terminate your active NSG session.\n\nNote: Single ARFCN lock will disable carrier aggregation. Multi-ARFCN lock is only supported for LTE.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = lteEarfcns,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() || it == ',' || it.isWhitespace() }) {
                            lteEarfcns = value
                            validationError = null
                        }
                    },
                    label = { Text("LTE EARFCN") },
                    placeholder = {
                        Text("Input up to 10 EARFCNs: 125,250,1275,9310,38852,39050,41490")
                    },
                    supportingText = { Text("Comma-separated • maximum 10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = false,
                    minLines = 2,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ltePci,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            ltePci = value
                            validationError = null
                        }
                    },
                    label = { Text("LTE PCI") },
                    placeholder = { Text("Input one PCI") },
                    supportingText = { Text("0–503") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nrArfcn,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            nrArfcn = value
                            validationError = null
                        }
                    },
                    label = { Text("NR-ARFCN") },
                    placeholder = { Text("Input one NR-ARFCN") },
                    supportingText = { Text("1–3279165") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nrPci,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            nrPci = value
                            validationError = null
                        }
                    },
                    label = { Text("NR PCI") },
                    placeholder = { Text("Input one PCI") },
                    supportingText = { Text("0–1007") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                )

                validationError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirmation = true },
                        enabled = !isRefreshing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                    Button(
                        onClick = {
                            parseFrequencyLock(lteEarfcns, ltePci, nrArfcn, nrPci)
                                .onSuccess { parsed ->
                                    validationError = null
                                    onApply(
                                        parsed.lteEarfcns,
                                        parsed.ltePci,
                                        parsed.nrArfcn,
                                        parsed.nrPci
                                    )
                                }
                                .onFailure { validationError = it.message }
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset cell locks?") },
            text = {
                Text("This clears all LTE and NR frequency/PCI constraints and restarts the cellular modem.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onReset()
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
