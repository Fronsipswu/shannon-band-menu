package fronsipswu.shannonbandmenu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.HardwareBands

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hardware: HardwareBands,
    visibleGsmBands: Set<Int>?,
    visibleWcdmaBands: Set<Int>?,
    visibleLteBands: Set<Int>?,
    visibleNrSaBands: Set<Int>?,
    visibleNrNsaBands: Set<Int>?,
    onSave: (Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    BackHandler(onBack = onBack)

    fun selectionMap(bands: Set<Int>, selected: Set<Int>?) =
        mutableStateMapOf<Int, Boolean>().apply {
            val visible = selected ?: bands
            bands.forEach { this[it] = it in visible }
        }

    val nrSaChecked = remember(visibleNrSaBands, hardware.nr) { selectionMap(hardware.nr, visibleNrSaBands) }
    val nrNsaChecked = remember(visibleNrNsaBands, hardware.nr) { selectionMap(hardware.nr, visibleNrNsaBands) }
    val lteChecked = remember(visibleLteBands, hardware.lte) { selectionMap(hardware.lte, visibleLteBands) }
    val wcdmaChecked = remember(visibleWcdmaBands, hardware.wcdma) { selectionMap(hardware.wcdma, visibleWcdmaBands) }
    val gsmChecked = remember(visibleGsmBands, hardware.gsm) { selectionMap(hardware.gsm, visibleGsmBands) }

    fun selected(map: Map<Int, Boolean>): Set<Int> = map.filterValues { it }.keys

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("Band display") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Choose which supported bands appear on the main screen and are included when applying a lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BandVisibilityCard("NR-SA bands", hardware.nr.sorted(), nrSaChecked, "n")
            BandVisibilityCard("NR-NSA bands", hardware.nr.sorted(), nrNsaChecked, "n")
            BandVisibilityCard("LTE bands", hardware.lte.sorted(), lteChecked, "B")
            BandVisibilityCard("WCDMA bands", hardware.wcdma.sorted(), wcdmaChecked, "B")
            BandVisibilityCard("GSM bands", hardware.gsm.sorted(), gsmChecked, "")

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        hardware.nr.forEach { nrSaChecked[it] = true; nrNsaChecked[it] = true }
                        hardware.lte.forEach { lteChecked[it] = true }
                        hardware.wcdma.forEach { wcdmaChecked[it] = true }
                        hardware.gsm.forEach { gsmChecked[it] = true }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset all") }

                Button(
                    onClick = {
                        val nrSa = selected(nrSaChecked)
                        val nrNsa = selected(nrNsaChecked)
                        val lte = selected(lteChecked)
                        val wcdma = selected(wcdmaChecked)
                        val gsm = selected(gsmChecked)
                        onSave(
                            nrSa.takeUnless { it == hardware.nr },
                            nrNsa.takeUnless { it == hardware.nr },
                            lte.takeUnless { it == hardware.lte },
                            wcdma.takeUnless { it == hardware.wcdma },
                            gsm.takeUnless { it == hardware.gsm }
                        )
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun BandVisibilityCard(
    title: String,
    bands: List<Int>,
    checked: MutableMap<Int, Boolean>,
    prefix: String
) {
    if (bands.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            BandCheckboxGrid(bands = bands, checked = checked, prefix = prefix)
        }
    }
}
