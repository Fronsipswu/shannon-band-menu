package fronsipswu.shannonbandmenu.diag

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Call from the UI thread. A replacement waits for all previous session cleanup. */
internal class DiagSessionRunner(private val scope: CoroutineScope) {
    private val ownership = Mutex()
    private var job: Job? = null

    fun replace(session: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch {
            // Joining only the previous job loses the original owner when an
            // intermediate restart is cancelled while waiting for cleanup.
            ownership.withLock { session() }
        }
    }
}
