package fronsipswu.shannonbandmenu.diag

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Process
import com.topjohnwu.superuser.Shell
import fronsipswu.shannonbandmenu.AppLog
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException

/** One app's native stats helper; it never starts/stops the optional broker. */
class DiagClient(private val context: Context) {
    companion object {
        const val VERSION = "0.4.2"
        private const val BINARY = "sbm-diagd"
    }

    data class Reading(val snapshot: DiagSnapshot?, val transport: String, val shared: Boolean?)

    private var socket: LocalSocket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val socketName = "sbm_diagd_${Process.myUid()}"
    private val stderr = File(context.cacheDir, "diagd-stderr.txt")
    private var lastHealth: String? = null

    fun stats(): Reading {
        if (socket == null) start()
        val reply = request("stats")
        if (reply.optString("version") != VERSION) throw IOException("DIAG helper version mismatch")
        val age = reply.optLong("age_ms", -1)
        val snapshot = reply.optJSONObject("snapshot")?.takeIf { age in 0..3000 }
            ?.let(DiagSnapshot::parse)
        val health = "${snapshot?.rat ?: "waiting"}; ${snapshot?.carriers?.size ?: 0} carriers"
        if (health != lastHealth) {
            AppLog.i("SBMDiag", "DIAG $health; sample age=$age ms")
            lastHealth = health
        }
        val session = reply.optJSONObject("session")
        val shared = session?.takeUnless { it.isNull("other_client_present") }
            ?.optBoolean("other_client_present")
        return Reading(snapshot, reply.optString("transport"), shared)
    }

    private fun start() {
        if (connect()) return
        val destination = File(context.filesDir, "$BINARY-$VERSION")
        val temporary = File(context.filesDir, "$BINARY.new")
        context.assets.open(BINARY).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.setExecutable(true, false)) { "Cannot make DIAG helper executable" }
        check(temporary.renameTo(destination)) { "Cannot install DIAG helper" }
        stderr.delete()
        val launch = "setsid '${destination.absolutePath}' --auto --serve ${Process.myUid()} " +
            "</dev/null >/dev/null 2>'${stderr.absolutePath}' &"
        val result = Shell.cmd(launch).exec()
        if (!result.isSuccess) throw IOException("Root access is required for DIAG")
        repeat(24) {
            Thread.sleep(250)
            if (connect()) return
            if (stderr.exists() && stderr.length() > 0) {
                throw IOException(stderr.readText().take(1500).trim())
            }
        }
        throw IOException("Cannot connect to the DIAG helper")
    }

    private fun connect(): Boolean {
        val candidate = LocalSocket()
        return try {
            candidate.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            candidate.soTimeout = 12_000
            if (candidate.peerCredentials.uid != 0) throw IOException("Unexpected DIAG socket owner")
            socket = candidate
            reader = candidate.inputStream.bufferedReader()
            writer = candidate.outputStream.bufferedWriter()
            true
        } catch (_: IOException) {
            candidate.close()
            false
        }
    }

    private fun request(command: String): JSONObject {
        val output = writer ?: throw IOException("DIAG is disconnected")
        output.write("{\"cmd\":\"$command\"}\n")
        output.flush()
        val line = reader?.readLine() ?: throw IOException(
            stderr.takeIf(File::exists)?.readText()?.take(1500)?.trim().orEmpty()
                .ifEmpty { "DIAG helper disconnected" }
        )
        val reply = JSONObject(line)
        if (!reply.optBoolean("ok")) throw IOException(reply.optString("error", "DIAG read failed"))
        return reply
    }

    fun stop() {
        try {
            if (socket != null) request("stop")
        } catch (_: Exception) {
            // The native watchdog also releases the lease after app loss.
        } finally {
            runCatching { socket?.close() }
            socket = null
            reader = null
            writer = null
        }
    }
}
