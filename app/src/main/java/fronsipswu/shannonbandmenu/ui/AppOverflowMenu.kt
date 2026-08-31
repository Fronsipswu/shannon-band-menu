package fronsipswu.shannonbandmenu.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Shared overflow actions shown from the top app bar on both main pages. */
@Composable
internal fun AppOverflowMenu(
    settingsEnabled: Boolean,
    onSettings: () -> Unit,
    debugEnabled: Boolean,
    onDebugToggle: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Band display settings") },
            enabled = settingsEnabled,
            onClick = {
                expanded = false
                onSettings()
            }
        )
        DropdownMenuItem(
            text = { Text("Debug logging") },
            trailingIcon = {
                if (debugEnabled) Icon(Icons.Outlined.Check, contentDescription = null)
            },
            onClick = {
                expanded = false
                onDebugToggle()
            }
        )
    }
}
