package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** QBM's padlock replay: play/pause and precise scrubbing with a time bubble. */
@Composable
internal fun NetworkReplayBar(
    size: Int,
    position: Int,
    wallMillis: Long,
    playing: Boolean,
    onPosition: (Int) -> Unit,
    onPlay: () -> Unit,
    onDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay, enabled = size > 1) {
                Icon(painterResource(if (playing) R.drawable.ic_test_pause else R.drawable.ic_test_play),
                    contentDescription = if (playing) "Pause replay" else "Play replay")
            }
            ScrubSlider(
                value = position,
                max = (size - 1).coerceAtLeast(0),
                onValue = onPosition,
                bubble = clock.format(Date(wallMillis)),
                onDragStart = onDragStart,
                modifier = Modifier.weight(1f).padding(end = 10.dp)
            )
        }
    }
}
