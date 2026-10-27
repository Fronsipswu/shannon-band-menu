package fronsipswu.shannonbandmenu.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** Generates HTTP traffic independently of DIAG so idle carriers can become active. */
class TrafficTest internal constructor(
    initial: Config = Config(),
    private val saveConfig: (Config) -> Unit = {}
) {
    data class Config(
        val getUrl: String = "",
        val postUrl: String = "",
        val upload: Boolean = false,
        /** Zero means run until stopped. */
        val seconds: Int = 0,
        val streams: Int = 1
    ) {
        val url get() = (if (upload) postUrl else getUrl).trim()

        fun normalized() = copy(
            getUrl = getUrl.trim(), postUrl = postUrl.trim(),
            seconds = seconds.coerceIn(0, 999_999), streams = streams.coerceIn(1, MAX_STREAMS)
        )
    }

    data class Run(
        val running: Boolean = false,
        val upload: Boolean = false,
        val mbps: Double = 0.0,
        val remaining: Int? = null,
        val error: String? = null
    )

    private class Session(val config: Config) {
        val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
        val bytes = AtomicLong()
        val endAt = if (config.seconds > 0) System.nanoTime() + config.seconds * 1_000_000_000L else 0L
        @Volatile var error: String? = null
        lateinit var job: Job
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableConfig = MutableStateFlow(initial.normalized())
    private val mutableRun = MutableStateFlow(Run())
    val config = mutableConfig.asStateFlow()
    val run = mutableRun.asStateFlow()
    private var active: Session? = null

    fun save(config: Config) {
        val normalized = config.normalized()
        mutableConfig.value = normalized
        saveConfig(normalized)
    }

    /** An unset or invalid URL opens setup; saving alone never starts traffic. */
    @Synchronized
    fun start(): Boolean {
        if (active != null) return true
        val config = mutableConfig.value
        if (!usableUrl(config.url)) return false
        val session = Session(config)
        session.job = scope.launch(start = CoroutineStart.LAZY) { execute(session) }
        active = session
        mutableRun.value = Run(true, config.upload, remaining = remaining(session.endAt))
        session.job.start()
        return true
    }

    @Synchronized
    fun stop() {
        val session = active ?: return
        active = null
        mutableRun.value = Run(upload = session.config.upload, error = session.error)
        session.job.cancel()
        // disconnect() can block on Android; it must not run on the UI thread.
        scope.launch { closeConnections(session) }
    }

    private suspend fun execute(session: Session) {
        try {
            coroutineScope {
                val workers = List(session.config.streams) { launch { stream(session) } }
                try {
                    var lastBytes = session.bytes.get()
                    var lastAt = System.nanoTime()
                    while (isActive) {
                        delay(1_000)
                        val now = System.nanoTime()
                        if (session.endAt != 0L && now >= session.endAt) break
                        val total = session.bytes.get()
                        val rate = (total - lastBytes) * 8.0 / (now - lastAt) * 1000.0
                        lastBytes = total
                        lastAt = now
                        synchronized(this@TrafficTest) {
                            if (active === session) {
                                mutableRun.value = Run(true, session.config.upload, rate,
                                    remaining(session.endAt), session.error)
                            }
                        }
                    }
                } finally {
                    workers.forEach { it.cancel() }
                    closeConnections(session)
                }
            }
        } finally {
            withContext(NonCancellable) { closeConnections(session) }
            synchronized(this) {
                // A previous run finishing cannot clear a newly started run.
                if (active === session) {
                    active = null
                    mutableRun.value = Run(upload = session.config.upload, error = session.error)
                }
            }
        }
    }

    private suspend fun stream(session: Session) {
        val buffer = ByteArray(64 * 1024).also { Random.nextBytes(it) }
        while (currentCoroutineContext().isActive && beforeDeadline(session.endAt)) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(requestUrl(session.config.url)).openConnection() as HttpURLConnection
                session.connections.add(connection)
                connection.connectTimeout = 5_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
                currentCoroutineContext().ensureActive()
                val requestEnd = System.nanoTime() + 60_000_000_000L
                if (session.config.upload) upload(connection, session, buffer, requestEnd)
                else download(connection, session, buffer, requestEnd)
                session.error = null
            } catch (error: Exception) {
                if (!currentCoroutineContext().isActive) break
                session.error = if (error is HttpFailure) "HTTP ${error.code}"
                    else error.javaClass.simpleName
                delay(1_000)
            } finally {
                connection?.let {
                    session.connections.remove(it)
                    runCatching { it.disconnect() }
                }
            }
        }
    }

    private suspend fun download(
        connection: HttpURLConnection, session: Session, buffer: ByteArray, requestEnd: Long
    ) {
        checkResponse(connection)
        connection.inputStream.use { input ->
            while (currentCoroutineContext().isActive && beforeDeadline(requestEnd) &&
                beforeDeadline(session.endAt)) {
                val count = input.read(buffer)
                if (count < 0) break
                session.bytes.addAndGet(count.toLong())
            }
        }
    }

    private suspend fun upload(
        connection: HttpURLConnection, session: Session, buffer: ByteArray, requestEnd: Long
    ) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setFixedLengthStreamingMode(25_000_000L)
        connection.outputStream.use { output ->
            var left = 25_000_000L
            while (left > 0 && currentCoroutineContext().isActive && beforeDeadline(requestEnd) &&
                beforeDeadline(session.endAt)) {
                val count = minOf(left, buffer.size.toLong()).toInt()
                output.write(buffer, 0, count)
                session.bytes.addAndGet(count.toLong())
                left -= count
            }
        }
        checkResponse(connection)
    }

    private class HttpFailure(val code: Int) : IOException()

    private fun checkResponse(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) throw HttpFailure(code)
    }

    private fun closeConnections(session: Session) {
        session.connections.forEach { runCatching { it.disconnect() } }
        session.connections.clear()
    }

    companion object {
        const val MAX_STREAMS = 8
        @Volatile private var instance: TrafficTest? = null

        fun get(context: Context): TrafficTest = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): TrafficTest {
            val prefs = context.getSharedPreferences("traffic_test", Context.MODE_PRIVATE)
            val config = Config(
                prefs.getString("get", "").orEmpty(), prefs.getString("post", "").orEmpty(),
                prefs.getBoolean("upload", false), prefs.getInt("seconds", 0), prefs.getInt("streams", 1)
            )
            return TrafficTest(config) { saved ->
                prefs.edit().putString("get", saved.getUrl).putString("post", saved.postUrl)
                    .putBoolean("upload", saved.upload).putInt("seconds", saved.seconds)
                    .putInt("streams", saved.streams).apply()
            }
        }

        fun usableUrl(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)

        internal fun requestUrl(value: String): String {
            val base = value.substringBefore('#')
            val separator = if ('?' in base) '&' else '?'
            return "$base${separator}run=${System.nanoTime()}_${Random.nextInt(32768)}"
        }

        private fun beforeDeadline(endAt: Long) = endAt == 0L || System.nanoTime() < endAt

        private fun remaining(endAt: Long): Int? = if (endAt == 0L) null else
            ((endAt - System.nanoTime() + 999_999_999L) / 1_000_000_000L).toInt().coerceAtLeast(0)
    }
}

/** HTTP payload rate, distinct from the independently measured DIAG PHY graph. */
fun trafficTestStatus(run: TrafficTest.Run): String = when {
    run.running -> buildString {
        append(if (run.upload) "Upload" else "Download")
        append(" | ").append(String.format(Locale.US, if (run.mbps >= 100) "%.0f" else "%.1f", run.mbps))
        append(" Mbps")
        run.remaining?.let { append(String.format(Locale.US, " | %d:%02d", it / 60, it % 60)) }
        run.error?.let { append(" | ").append(it) }
    }
    run.error != null -> "Test stopped: ${run.error}"
    else -> ""
}
