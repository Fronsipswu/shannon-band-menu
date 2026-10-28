package fronsipswu.shannonbandmenu.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Real HTTP requests stay on loopback; no modem or external service is involved. */
class TrafficTestTest {
    private lateinit var server: HttpServer
    private val executor = Executors.newCachedThreadPool()
    private val runners = mutableListOf<TrafficTest>()
    private val downloads = AtomicInteger()
    private val downloadEncodings = ConcurrentHashMap.newKeySet<String>()
    private val uploads = AtomicLong()
    private val failures = AtomicInteger()
    private lateinit var base: String

    @Before fun setup() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        server.executor = executor
        server.createContext("/download") { exchange ->
            downloads.incrementAndGet()
            downloadEncodings.add(exchange.requestHeaders.getFirst("Accept-Encoding") ?: "unset")
            try {
                val body = ByteArray(16 * 1024)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
                Thread.sleep(10)
            } finally {
                exchange.close()
            }
        }
        server.createContext("/upload") { exchange ->
            try {
                if (exchange.requestMethod != "POST") {
                    exchange.sendResponseHeaders(405, -1)
                } else {
                    val buffer = ByteArray(64 * 1024)
                    exchange.requestBody.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            uploads.addAndGet(count.toLong())
                            Thread.sleep(1)
                        }
                    }
                    exchange.sendResponseHeaders(204, -1)
                }
            } finally {
                exchange.close()
            }
        }
        server.createContext("/failure") { exchange ->
            failures.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    private fun runner(config: TrafficTest.Config) = TrafficTest(config).also { runners.add(it) }

    @After fun cleanup() {
        runners.forEach { it.stop() }
        server.stop(0)
        executor.shutdownNow()
    }

    @Test fun validatesTheSelectedModeAndNormalizesSettings() {
        assertFalse(TrafficTest.usableUrl("http://"))
        assertFalse(TrafficTest.usableUrl("file:///tmp/input"))
        assertFalse(TrafficTest.usableUrl("https://bad host/path"))
        assertTrue(TrafficTest.usableUrl(" HTTPS://example.com/test "))
        val test = runner(TrafficTest.Config(getUrl = "$base/download", upload = true))
        assertFalse(test.start())
        assertEquals(0, downloads.get())
        test.save(TrafficTest.Config(getUrl = " $base/download ", seconds = -1, streams = 99))
        assertEquals("$base/download", test.config.value.getUrl)
        assertEquals(0, test.config.value.seconds)
        assertEquals(8, test.config.value.streams)
    }

    @Test fun cacheBusterPreservesQueriesAndRemovesFragments() {
        val url = TrafficTest.requestUrl("$base/download?size=1024#section")
        assertTrue(url.startsWith("$base/download?size=1024&run="))
        assertFalse(url.contains('#'))
        assertTrue(TrafficTest.requestUrl("$base/download").contains("?run="))
    }

    @Test fun parallelDownloadReportsPayloadRateAndStopsAtDuration() = runBlocking {
        val test = runner(TrafficTest.Config(getUrl = "$base/download", seconds = 3, streams = 2))
        assertTrue(test.start())
        val active = withTimeout(2500) { test.run.first { it.running && it.mbps > 0 } }
        assertFalse(active.upload)
        assertNotNull(active.remaining)
        withTimeout(3500) { test.run.first { !it.running } }
        assertTrue(downloads.get() > 2)
        assertEquals(setOf("identity"), downloadEncodings)
        assertNull(test.run.value.error)
    }

    @Test fun parallelDownloadRateMatchesReceivedPayloadRate() = runBlocking {
        val sent = AtomicLong()
        server.createContext("/paced-download") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                val block = ByteArray(16 * 1024)
                val until = System.nanoTime() + 8_000_000_000L
                exchange.responseBody.use { output ->
                    while (System.nanoTime() < until) {
                        output.write(block)
                        output.flush()
                        sent.addAndGet(block.size.toLong())
                        Thread.sleep(16)
                    }
                }
            } catch (_: java.io.IOException) {
                // Stopping the client closes the local streaming response.
            } finally {
                exchange.close()
            }
        }
        val test = runner(TrafficTest.Config(getUrl = "$base/paced-download", streams = 2, seconds = 6))
        assertTrue(test.start())
        val samples = withTimeout(5500) {
            test.run.filter { it.running && it.mbps > 0 }.map {
                Triple(it.mbps, sent.get(), System.nanoTime())
            }.take(3).toList()
        }
        test.stop()
        for ((previous, current) in samples.zipWithNext()) {
            val expected = (current.second - previous.second) * 8.0 /
                (current.third - previous.third) * 1000.0
            // Independent server writes and client sampling can straddle a block.
            assertEquals("Two-stream payload rate", expected, current.first, maxOf(1.0, expected * 0.15))
        }
    }

    @Test fun uploadUsesPostAndGeneratedPayload() = runBlocking {
        val test = runner(TrafficTest.Config(postUrl = "$base/upload", upload = true, seconds = 1))
        assertTrue(test.start())
        withTimeout(4000) { test.run.first { !it.running } }
        assertTrue(uploads.get() > 0)
        assertTrue(test.run.value.upload)
    }

    @Test fun httpFailureIsReportedWithoutPrintingTheUrl() = runBlocking {
        val test = runner(TrafficTest.Config(getUrl = "$base/failure?token=private", seconds = 1))
        assertTrue(test.start())
        withTimeout(4000) { test.run.first { !it.running } }
        assertEquals("HTTP 503", test.run.value.error)
        assertFalse(trafficTestStatus(test.run.value).contains("private"))
        assertTrue(failures.get() > 0)
    }

    @Test fun stoppingAndRestartingCannotBeClearedByThePreviousRun() = runBlocking {
        val test = runner(TrafficTest.Config(getUrl = "$base/download", seconds = 1))
        assertTrue(test.start())
        delay(100)
        test.stop()
        assertFalse(test.run.value.running)
        test.save(test.config.value.copy(seconds = 3))
        assertTrue(test.start())
        delay(1300)
        assertTrue(test.run.value.running)
        test.stop()
        assertFalse(test.run.value.running)
    }

    @Test fun statusShowsDirectionCountdownAndErrors() {
        assertEquals("Upload | 12.5 Mbps | 1:05", trafficTestStatus(
            TrafficTest.Run(running = true, upload = true, mbps = 12.5, remaining = 65)))
        assertEquals("", trafficTestStatus(TrafficTest.Run()))
        assertEquals("Test stopped: HTTP 503", trafficTestStatus(TrafficTest.Run(error = "HTTP 503")))
    }
}
