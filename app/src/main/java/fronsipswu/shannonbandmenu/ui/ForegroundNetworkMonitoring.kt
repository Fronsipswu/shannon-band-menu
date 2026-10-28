package fronsipswu.shannonbandmenu.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import fronsipswu.shannonbandmenu.diag.DiagMonitor
import fronsipswu.shannonbandmenu.network.NetworkReplayModel

/** Lives above the pager so switching tabs cannot stop collection or release the DIAG lease. */
@Composable
internal fun ForegroundNetworkMonitoring() {
    val activity = LocalContext.current as ComponentActivity
    val monitor = remember(activity) { ViewModelProvider(activity)[DiagMonitor::class.java] }
    val replay = remember(activity) { ViewModelProvider(activity)[NetworkReplayModel::class.java] }
    DisposableEffect(activity) {
        replay.bind(monitor)
        fun update() = replay.setForeground(
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        val observer = LifecycleEventObserver { _, _ -> update() }
        activity.lifecycle.addObserver(observer)
        update()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            replay.setForeground(false)
        }
    }
}
