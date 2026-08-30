package dev.qcom.bandmenu.ui

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
import dev.qcom.bandmenu.HardwareBands
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * Controls which LTE/NR entries are available in the main page and Apply.
 * Hidden bands remain part of the modem capability query, but are excluded
 * from the next Apply payload until enabled here again.
 */
@Composable
fun SettingsScreen(
    hardware: HardwareBands,
    visibleLteBands: Set<Int>?,
    visibleNrBands: Set<Int>?,
    onSave: (Set<Int>?, Set<Int>?) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    BackHandler(onBack = onBack)

    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues(density).calculateBottomPadding()
    val bottomSpace = 88.dp + navInset
    val lteBands = hardware.lte.sorted()
    val nrBands = hardware.nr.sorted()

    val lteChecked = remember(visibleLteBands, hardware.lte) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleLteBands ?: hardware.lte
            hardware.lte.forEach { this[it] = it in selected }
        }
    }
    val nrChecked = remember(visibleNrBands, hardware.nr) {
        mutableStateMapOf<Int, Boolean>().apply {
            val selected = visibleNrBands ?: hardware.nr
            hardware.nr.forEach { this[it] = it in selected }
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
                "Choose which LTE and NR bands to use on the main page.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

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

            if (nrBands.isNotEmpty()) {
                SmallTitle("NR bands")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        bands = nrBands,
                        checked = nrChecked,
                        prefix = "n"
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
                        hardware.lte.forEach { lteChecked[it] = true }
                        hardware.nr.forEach { nrChecked[it] = true }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val selectedLte = selected(lteChecked)
                        val selectedNr = selected(nrChecked)
                        onSave(
                            selectedLte.takeUnless { it == hardware.lte },
                            selectedNr.takeUnless { it == hardware.nr }
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
