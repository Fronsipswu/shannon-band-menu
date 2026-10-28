# Test-script status throughput

The status text uses the HTTP payload byte counter in
`network/TrafficTest.kt`, independently of DIAG. Each download worker counts
bytes returned by `InputStream.read`; all workers share one atomic counter.
About once per second, the reporter computes:

```
elapsedSeconds = (nowNanos - previousNanos) / 1e9
Mbps = (bytesNow - bytesBefore) * 8 / elapsedSeconds / 1e6
```

This is the same calculation as public QBM's `network/TrafficTest.kt`.
SBM previously expressed the equivalent nanosecond conversion as `* 1000`.
The interval includes request setup, retries and server pauses; the reading
is a short-interval payload rate, not a whole-test average or DIAG PHY rate.
The display rounds values at/above 100 Mbps to a whole number, as QBM does.

Android's `HttpURLConnection` requests gzip and transparently decompresses it
unless Accept-Encoding is explicitly set. Counting that decoded stream can
inflate a speed reading for compressible responses. SBM now requests
`Accept-Encoding: identity`, which also disables automatic decompression.
There is no body decoding in the traffic generator. QBM's reviewed source
does not set this header. This is a verified API risk; the user's reported
intermittent difference has not been reproduced on the phone.

Reference: [Android HttpURLConnection, Performance](https://developer.android.com/reference/java/net/HttpURLConnection).

`TrafficTestTest.parallelDownloadRateMatchesReceivedPayloadRate` runs two
paced localhost streams and compares the displayed interval rate with the
server's independently counted writes over the corresponding measured times.
It allows for a data block straddling a sampling boundary. The download test
also checks that every request specifies identity encoding. These checks
generate only PC loopback traffic. No agent-initiated cellular test or request
to the user's configured endpoint was used for this comparison.
