package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.BuildConfig
import fronsipswu.shannonbandmenu.HardwareBands

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    contentPadding: PaddingValues = PaddingValues(),
    debugEnabled: Boolean = false,
    onDebugToggle: () -> Unit = {},
    hardware: HardwareBands? = null,
    visibleGsmBands: Set<Int>? = null,
    visibleWcdmaBands: Set<Int>? = null,
    visibleLteBands: Set<Int>? = null,
    visibleNrSaBands: Set<Int>? = null,
    visibleNrNsaBands: Set<Int>? = null,
    onBandVisibilitySave: (Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?) -> Unit = { _, _, _, _, _ -> }
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings && hardware != null) {
        SettingsScreen(
            hardware = hardware,
            visibleGsmBands = visibleGsmBands,
            visibleWcdmaBands = visibleWcdmaBands,
            visibleLteBands = visibleLteBands,
            visibleNrSaBands = visibleNrSaBands,
            visibleNrNsaBands = visibleNrNsaBands,
            onSave = onBandVisibilitySave,
            onBack = { showSettings = false },
            contentPadding = contentPadding
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        TopAppBar(
            title = { Text("Info") },
            actions = {
                AppOverflowMenu(
                    settingsEnabled = hardware != null,
                    onSettings = { if (hardware != null) showSettings = true },
                    debugEnabled = debugEnabled,
                    onDebugToggle = onDebugToggle
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard("Shannon Band Menu") {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            InfoCard("Base UI design by") {
                Text(
                    "@h3nnes",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
