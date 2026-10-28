package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The relay reads this setting while running; a module restart is unnecessary. */
@Composable
internal fun RelayIdleTimerDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val control = "/data/adb/modules/shannon_dm_relay/control.sh"
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { Shell.cmd("sh $control idle-timeout").exec() }
        if (result.isSuccess) minutes = result.out.lastOrNull().orEmpty()
        else error = "Install relay 0.1.7 or newer to use the idle timer."
        working = false
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Relay idle timer") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Stop and restore diagnostic access after no apps use the relay for this many minutes. 0 disables the timer.")
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    enabled = !working, label = { Text("Minutes (0–1440)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working && minutes.toIntOrNull()?.let { it in 0..1440 } == true, onClick = {
                val value = minutes.toIntOrNull() ?: return@TextButton
                working = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        Shell.cmd("sh $control idle-timeout $value").exec()
                    }
                    working = false
                    if (result.isSuccess) onDismiss()
                    else error = "Cannot update the relay timer. Root access and relay 0.1.7 are required."
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } }
    )
}
