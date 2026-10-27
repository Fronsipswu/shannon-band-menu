package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.R
import fronsipswu.shannonbandmenu.network.TrafficTest
import fronsipswu.shannonbandmenu.network.trafficTestStatus

@Composable
internal fun TrafficTestButton(run: TrafficTest.Run, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(painterResource(if (run.running) R.drawable.ic_test_pause else R.drawable.ic_test_play),
            contentDescription = if (run.running) "Stop test script" else "Start test script",
            tint = if (run.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Keeps traffic-test status live even when the radio measurements are frozen. */
@Composable
internal fun TrafficTestStatus(run: TrafficTest.Run) {
    val status = trafficTestStatus(run)
    if (status.isEmpty()) return
    Text(status, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), maxLines = 1,
        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
        color = if (run.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}
