package fronsipswu.shannonbandmenu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.HardwareBands
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * Controls which band entries are available in the main page and Apply.
 * Hidden bands remain part of the modem capability query, but are excluded
 * from the next Apply payload until enabled here again.
 */
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

    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues(density).calculateBottomPadding()
    val bottomSpace = 88.dp + navInset
    val nrBands = hardware.nr.sorted()
    val lteBands = hardware.lte.sorted()
    val wcdmaBands = hardware.wcdma.sorted()
    val gsmBands = hardware.gsm.sorted()

    val nrSaChecked = remember(visibleNrSaBands, hardware.nr) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleNrSaBands ?: hardware.nr
            hardware.nr.forEach { this[it] = it in selected }
        }
    }
    val nrNsaChecked = remember(visibleNrNsaBands, hardware.nr) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleNrNsaBands ?: hardware.nr
            hardware.nr.forEach { this[it] = it in selected }
        }
    }
    val lteChecked = remember(visibleLteBands, hardware.lte) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleLteBands ?: hardware.lte
            hardware.lte.forEach { this[it] = it in selected }
        }
    }
    val wcdmaChecked = remember(visibleWcdmaBands, hardware.wcdma) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleWcdmaBands ?: hardware.wcdma
            hardware.wcdma.forEach { this[it] = it in selected }
        }
    }
    val gsmChecked = remember(visibleGsmBands, hardware.gsm) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleGsmBands ?: hardware.gsm
            hardware.gsm.forEach { this[it] = it in selected }
        }
    }

    fun selected(map: Map<Int, Boolean>): Set<Int> = map.filterValues { it }.keys

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomSpace)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            SmallTitle("Settings")
            Text(
                "Choose which bands to use on the main page.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (nrBands.isNotEmpty()) {
                SmallTitle("NR-SA bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = nrBands,
                        checked = nrSaChecked,
                        prefix = "n"
                    )
                }
                SmallTitle("NR-NSA bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = nrBands,
                        checked = nrNsaChecked,
                        prefix = "n"
                    )
                }
            }

            if (lteBands.isNotEmpty()) {
                SmallTitle("LTE bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = lteBands,
                        checked = lteChecked,
                        prefix = "B"
                    )
                }
            }

            if (wcdmaBands.isNotEmpty()) {
                SmallTitle("WCDMA bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = wcdmaBands,
                        checked = wcdmaChecked,
                        prefix = "B"
                    )
                }
            }

            if (gsmBands.isNotEmpty()) {
                SmallTitle("GSM bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = gsmBands,
                        checked = gsmChecked,
                        prefix = ""
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "Reset all",
                    onClick = {
                        hardware.nr.forEach { nrSaChecked[it] = true; nrNsaChecked[it] = true }
                        hardware.lte.forEach { lteChecked[it] = true }
                        hardware.wcdma.forEach { wcdmaChecked[it] = true }
                        hardware.gsm.forEach { gsmChecked[it] = true }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val selectedNrSa = selected(nrSaChecked)
                        val selectedNrNsa = selected(nrNsaChecked)
                        val selectedLte = selected(lteChecked)
                        val selectedWcdma = selected(wcdmaChecked)
                        val selectedGsm = selected(gsmChecked)
                        onSave(
                            selectedNrSa.takeUnless { it == hardware.nr },
                            selectedNrNsa.takeUnless { it == hardware.nr },
                            selectedLte.takeUnless { it == hardware.lte },
                            selectedWcdma.takeUnless { it == hardware.wcdma },
                            selectedGsm.takeUnless { it == hardware.gsm }
                        )
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("Save")
                }
            }

            TextButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

    }
}
