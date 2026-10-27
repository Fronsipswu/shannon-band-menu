package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.network.TrafficTest

/** Configure both endpoints; starting traffic remains an explicit play action. */
@Composable
fun TestScriptDialog(
    show: Boolean,
    config: TrafficTest.Config,
    onDismiss: () -> Unit,
    onSave: (TrafficTest.Config) -> Unit
) {
    if (!show) return
    var getUrl by remember { mutableStateOf(config.getUrl) }
    var postUrl by remember { mutableStateOf(config.postUrl) }
    var upload by remember { mutableStateOf(config.upload) }
    var seconds by remember { mutableStateOf(config.seconds.takeIf { it > 0 }?.toString().orEmpty()) }
    var streams by remember { mutableIntStateOf(config.streams.coerceIn(1, TrafficTest.MAX_STREAMS)) }
    val activeUrl = if (upload) postUrl else getUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test script") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = getUrl, onValueChange = { getUrl = it },
                    label = { Text("HTTP GET (download)") }, placeholder = { Text("https://…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = getUrl.isNotBlank() && !TrafficTest.usableUrl(getUrl),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = postUrl, onValueChange = { postUrl = it },
                    label = { Text("HTTP POST (upload)") }, placeholder = { Text("https://…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = postUrl.isNotBlank() && !TrafficTest.usableUrl(postUrl),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
                Text("Streams", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { streams-- }, enabled = streams > 1, modifier = Modifier.width(64.dp)) {
                        Text("−")
                    }
                    Text("$streams", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { streams++ }, enabled = streams < TrafficTest.MAX_STREAMS,
                        modifier = Modifier.width(64.dp)) { Text("+") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !upload, onClick = { upload = false },
                        label = { Text("Download") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = upload, onClick = { upload = true },
                        label = { Text("Upload") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = seconds, onValueChange = { seconds = it.filter(Char::isDigit).take(6) },
                    label = { Text("Duration (seconds)") }, placeholder = { Text("Until stopped") },
                    supportingText = { Text("0 = until stopped") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = TrafficTest.usableUrl(activeUrl),
                onClick = {
                    onSave(TrafficTest.Config(getUrl, postUrl, upload, seconds.toIntOrNull() ?: 0, streams))
                }
            ) { Text("Save") }
        }
    )
}
