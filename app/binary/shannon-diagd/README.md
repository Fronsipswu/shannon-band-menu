# SBM DIAG helper

`sbm-diagd` 0.4.0 is a separate root process from the band-lock daemon and the Android app. It frames Shannon DM packets, decodes the capture-validated LTE/NR layouts, aggregates completed one-second windows, and serves structured metrics to the app. It contains no band-lock, NV or service-menu operations.

## Build

From the Android project directory, with Android NDK installed:

```powershell
.\binary\shannon-diagd\build.ps1 -Ndk D:\AndroidSDK\ndk\30.0.16138531
```

The script updates `src/main/assets/sbm-diagd`. The asset is included in the repository; Gradle does not compile C. Build it whenever the native source changes. Native `VERSION` and Kotlin `DiagClient.VERSION` must match. The binary is static Android ARM64, API 26+, built with fortification, stack protection, LTO and section garbage collection. Handwritten source uses warnings as errors; warnings are suppressed only for the unchanged generated ASN.1 source.

## App protocol

The app extracts a versioned executable into its files directory, launches it through its granted root shell with `--auto --serve <app UID>`, and requests stats roughly once per second. The abstract socket is `sbm_diagd_<UID>`. Peer credentials must match that UID or root; the app checks that the server is root. Different app/test UIDs have different sockets. Binding happens before transport activation, so a duplicate helper exits without activating the modem.

```json
{"cmd":"stats"}
{"cmd":"stop"}
```

Replies are one JSON object per line. Stats contain `ok`, `version`, `transport`, the broker's initial Open decision as `session`, sample `age_ms`, and a schema-1 `snapshot` or null. The nested snapshot has no embedded line terminator. Missing/old measurements remain null. This endpoint accepts no arbitrary modem commands. Fifteen seconds without a valid stats request causes helper exit and lease release. Stop or normal termination does the same; app lifecycle stops polling and releases the helper when the page/activity leaves the foreground. Display freeze keeps health polling alive while holding the displayed reading.

App session replacements hold a mutex through connection cleanup, so rapid pause/resume transitions cannot connect a second client before the previous one releases the helper. Version 0.3.1 also records app Stop requests, rejected overlapping connections and input closure in the helper's diagnostic stderr file.

The Network tab remains active throughout scroll gestures. Monitoring changes with the settled tab and activity lifecycle, rather than the pager's transient scrolling flag. A successful stats response establishes the connection even if the first completed measurement window is not available yet.

Visible paused states (lifecycle STARTED), including Quick Settings overlays, keep monitoring alive so event-driven channel configuration can be received during an airplane-mode toggle. A stopped activity still releases its helper; configuration received while it is stopped is not replayed by the broker.

## Transports

- Active compatible broker: connect its root-only receive tap before logical Open; hold the control connection for the lease lifetime. Closing the helper releases its own lease. The helper sends no raw modem commands in this mode.
- Version 0.3.2 can reattach a closed receive tap while its control lease remains connected (at most three retries per ten-second interval). It verifies the new peer, clears cached measurements and partial framing, and skips the incomplete throughput window. A dead broker or persistent drops still ends the session. Relay 0.1.6 improves burst buffering; recovery does not replace fixing an overloaded producer/consumer path.
- Broker absent/refused connection: inspect `/dev/umts_dm0` owners, including aliases by device identity. Refuse busy/unknown ownership; otherwise take the helper lock, open, recheck, and send the known LTE activation sequence.
- A responding incompatible/refusing broker is never bypassed. The helper does not start the module, close other apps, stop Android services, or change USB/build properties or SELinux policy.

Owner inspection is best effort; the helper lock cannot exclude unrelated direct readers. Independent fresh-boot NR activation and full physical logging quiescence after the known Stop request remain unverified. Broker logical Close is not proof that all background modem logging stopped.

## UI and coverage

The Kotlin layer parses finite values, preserves nulls/real zeros, and attributes unlabelled modem data only to a uniquely matching subscription (or the sole subscription). Missing samples retain DIAG tables with identity placeholders; previous RF readings are not held as new measurements. API identity/configuration can fill otherwise unknown columns. Switching off DIAG returns to the original API tables.

The current decoder provides LTE/NR carrier identity, signal, throughput, scheduling and power fields where available. Unknown layouts are skipped. NR Port/Rx includes CSI-RS ports from received RRC configuration and the RX count. Per-chain SINR remains unavailable. The graph distinguishes missing DL/UL from a measured zero. Neighbour-cell decoding is outside this implementation.

Version 0.4.4 includes per-carrier NR uplink modulation, layers and retransmission
BLER from the supported PUSCH records. Periodic zero MCS samples no longer erase
a recent positive report. The uplink card displays Rank 1 or 2 from PUSCH layers and
uses QBM's green-to-red transmit-power scale for PUSCH and PUCCH. See
[uplink findings and limits](UPLINK-2026-10-28.md).

The helper's NR `rx` field comes from channel-configuration records. In the app,
NR Port/Rx prefers the number of currently valid RX0..RX3 RSRP reports (strictly
between -140 and -20 dBm), using configured `rx` only when no usable chain
measurements exist. This displays measured chains, not a hardware capability or
a count inferred from rank. Current ID-31 measurements provide this for the
PCell; SCells without per-chain RSRP retain the configuration fallback. The
existing measurement freshness rules prevent stale chain arrays being used.

This choice was checked against serving/measurement records in
`test_10271955_NR_Measurements`, `test_09182104`, and `test_09181844` and their
NSG JSON exports. Reported RSRP-chain counts vary during the captures and do not
always equal NSG's `PCell_Num_Antenna_Rx` at an aligned second. The UI therefore
uses measured-chain occupancy as requested, without claiming to reproduce
NSG's RX-mode selection. Zero-filled PDSCH/CSI SINR entries cannot be used to
count active receivers. `NrRxCountTest` checks 4-to-1 changes, noncontiguous
chains, invalid samples, configuration fallback and independence from rank.

Version 0.4.0 adds a Shannon NR RRC wrapper and the trimmed ASN.1 CSI consumer, with per-SIM display hold behavior for idle ports. See [RRC decoder notes](rrc/README.md) for supported messages, bounds, state handling and verification limits.

Version 0.3.4 emits per-carrier `uplink_active` from a positive reported UL rate or actual UL RB allocation in the supported grant records, expiring after three seconds without evidence. Uplink tables retain primary carriers and include SCells only while that flag is true. Generic debug MCS/power values and synthesized zero rates do not establish an uplink SCell. This reflects recently observed activity, not a complete RRC uplink-configuration catalogue. NR carriers without their own UL evidence no longer inherit zero UL throughput/PRBs from another carrier.

Version 0.3.3 shows the actual rank indicator before the separate layer field, aggregating valid rank reports by maximum within each completed second. For the supported 48-byte NR ID-6 record, downlink modulation follows the previously capture-validated 256-QAM-table mapping of the maximum usable MCS: 0–4 QPSK, 5–10 16QAM, 11–19 64QAM and 20–27 256QAM. Reserved/retransmission indices 28–31 are excluded. This mapping does not establish other table/version support or uplink modulation. NR Beam and SCS rows are omitted from the UI; their decoded fields remain available internally.

## Tests

`test_metrics.py --binary <host-helper>` checks stats framing, explicit Stop, app-loss timeout, duplicate-start exclusion and cross-UID rejection (the latter needs root on Linux). Host builds link `main.c`, `telemetry.c`, `rrc/shannon_rrc.c`, `rrc/rrc_csi.c`, `rrc/rrc_nr_gen.c` and `-lm`; the tests use synthetic stdin and never open a modem. App tests cover schema handling, SIM attribution, serving identities, missing-sample layout retention, NR RB scales and the ported graph scale/window rules.

For an isolated phone APK, run `gradlew.bat testDebugUnitTest assembleDiagTest`. The package is `fronsipswu.shannonbandmenu.diagtest`, labelled **SBM DIAG Test**. The normal app package is separate.
