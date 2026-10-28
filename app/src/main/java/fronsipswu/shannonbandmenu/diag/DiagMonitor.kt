package fronsipswu.shannonbandmenu.diag

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fronsipswu.shannonbandmenu.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Monitoring follows app visibility; tab selection and freeze affect presentation only. */
class DiagMonitor(application: Application) : AndroidViewModel(application) {
    data class State(
        val enabled: Boolean = false,
        val starting: Boolean = false,
        val reading: DiagClient.Reading? = null,
        val error: String? = null,
        val sampledAtMillis: Long = 0,
        val points: List<ThputPoint> = emptyList()
    )

    private val preferences = application.getSharedPreferences("network-diag", 0)
    private val mutableState = MutableStateFlow(State(enabled = preferences.getBoolean("enabled", false)))
    val state = mutableState.asStateFlow()
    private var active = false
    private val sessions = DiagSessionRunner(viewModelScope)
    private val portHolds = mutableMapOf<Int, NrPortHold>()

    fun holdPorts(subscriptionId: Int, carriers: List<DiagCarrier>): List<DiagCarrier> =
        portHolds.getOrPut(subscriptionId) { NrPortHold() }.apply(carriers)

    fun setEnabled(enabled: Boolean) {
        portHolds.clear()
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableState.update { State(enabled = enabled) }
        reschedule()
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        reschedule()
    }

    private fun reschedule() {
        sessions.replace {
            if (!active || !mutableState.value.enabled) {
                mutableState.update { it.copy(starting = false) }
                return@replace
            }
            val client = DiagClient(getApplication())
            var lastWindow: Long? = null
            var lastIdentity: List<String>? = null
            mutableState.update { it.copy(starting = true) }
            try {
                while (isActive) {
                    val reading = withContext(Dispatchers.IO) { client.stats() }
                    val snapshot = reading.snapshot
                    val identity = snapshot?.carriers?.filter { it.primary }?.map {
                        "${it.nr}:${it.arfcn}:${it.pci}"
                    }
                    val now = SystemClock.elapsedRealtime()
                    mutableState.update { old ->
                        val point = snapshot?.takeIf {
                            it.window != lastWindow && (it.dlMbps != null || it.ulMbps != null)
                        }?.let { ThputPoint(now, it.dlMbps, it.ulMbps) }
                        val history = if (lastIdentity != null && identity != lastIdentity) emptyList()
                            else old.points
                        old.copy(
                            starting = false,
                            reading = reading,
                            error = null,
                            sampledAtMillis = now,
                            points = (history + listOfNotNull(point)).filter { now - it.t <= 125_000 }
                        )
                    }
                    lastWindow = snapshot?.window
                    lastIdentity = identity
                    delay(1_000)
                }
            } catch (error: Exception) {
                if (isActive) {
                    AppLog.e("SBMDiag", "DIAG session failed", error)
                    preferences.edit().putBoolean("enabled", false).apply()
                    mutableState.update {
                        it.copy(enabled = false, starting = false, reading = null,
                            error = error.message ?: "DIAG failed")
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { client.stop() }
            }
        }
    }
}
