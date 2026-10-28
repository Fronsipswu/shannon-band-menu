# Network page replay

SBM keeps a rolling history while the app is in the foreground, on every tab.
Tap the Network page's padlock or double tap its content to freeze at the newest
reading. The bottom bar provides play/pause and QBM's precise timeline slider.
Only dragging changes the position; moving the finger 80 dp away vertically
slows scrubbing to one tenth speed. While held, the selected reading's clock
time appears in a popup above the thumb. Unlock or double tap again to resume
current measurements. Playback follows the recorded sample intervals. The
traffic-test play button remains a separate control.

Collection continues while the Network page is frozen and while viewing Bands,
Cell Lock, or Info. Unlock and lock again to open an updated timeline. Hiding the
activity stops collection and releases the helper session; retained readings
and history stay available for the next foreground visit. Tab changes do not
close or reopen DIAG. Replay playback pauses when leaving the Network page.
Starting a timeline drag refreshes the recording while preserving the selected
timestamp, matching QBM's behavior when the history grows or old readings expire.

History is held in an activity ViewModel, survives rotation and tab changes,
and disappears with the process. There is no recording service, persistent
recording preference, file export, or raw modem packet capture. This adapts
QBM's in-app padlock and timeline interaction to SBM's Android and Shannon
metric snapshots.

`network/NetworkReplay.kt` retains at most one reading per elapsed second,
3,600 readings, one hour, and an estimated 24 MiB, whichever limit is reached
first. Size accounting conservatively estimates Android cells, carrier JSON,
port-held carrier copies, and graph lists; it is not an exact JVM heap limit.
An opened replay owns a stable list of references, so ongoing collection cannot
evict its selected reading. That list can retain another bounded history while
the live buffer advances.

Each frame retains the network subscriptions, DIAG mode/status, transport,
SIM attribution, resolved carrier tables, and corresponding throughput history.
`ui/ForegroundNetworkMonitoring.kt` owns the activity visibility observation
above the pager. `NetworkReplayModel` polls Android telephony, combines it with
the DIAG monitor, resolves carrier attribution and holds, and retains the latest
frame. The Network page observes that state; disposing its composition cannot
stop polling or erase its latest reading.

The replay chart combines the retained throughput points across the whole opened
timeline, including measurements after the selected reading. QBM's existing
two-minute chart positions the cursor at the right edge on the newest sample;
scrubbing back moves it toward the middle, where it stays once at least a minute
of newer data is available. Its window, line gaps, and autoscaling stay the same.

CSI port and bandwidth holds run only on live readings; seeking never feeds old
measurements back into the live caches. Widths are retained per subscription and
matching SSB NR-ARFCN/PCI; each newly valid width replaces the previous value.
The helper's RRC configuration fallback is documented in
`binary/shannon-diagd/rrc/README.md`. Missing measurements remain missing when
neither a source nor a matching held configuration is available.

Validation: `NetworkReplayTest` checks stable frozen timelines, duplicate polling,
time eviction across gaps, the byte budget, and future graph samples. QBM's
`ScrubSliderTest` covers fractional drag progress before recomposition and
resynchronization when the timeline changes. `ThroughputScaleTest` covers the
graph cursor moving from the right edge to the middle.
`assembleDiagTest` builds the isolated app for device verification.
