package fronsipswu.shannonbandmenu.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import fronsipswu.shannonbandmenu.diag.DiagClient

@Composable
internal fun DiagConnectionWarning(enabled: Boolean, reading: DiagClient.Reading?) {
    val shared = reading?.shared
    val available = enabled && reading != null
    var dismissed by remember(available, shared) { mutableStateOf(false) }
    if (available && shared != false && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("DIAG connection warning") },
            text = {
                Text(if (shared == true)
                    "Another client is using DIAG. SBM is connecting anyway. Clients may affect each other's logging or receive incomplete data."
                else "DIAG ownership could not be checked. SBM is connecting anyway.")
            },
            confirmButton = { TextButton(onClick = { dismissed = true }) { Text("OK") } }
        )
    }
}
