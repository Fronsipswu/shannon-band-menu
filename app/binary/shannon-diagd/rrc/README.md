# NR CSI-RS port counts

The generated `rrc_nr_gen.c/.h` pair is copied unchanged from the public QBM decoder. It is asn1c UPER output for the reduced 38.331 CSI path, including its runtime and embedded attribution. Do not hand-edit generated files. `rrc_csi.c` adapts QBM's CSI configuration consumer; its Qualcomm transport wrapper is replaced by an interface accepting complete NR messages.

`shannon_rrc.c` handles the current ICD 9.0, stack-1 `0x0801` record. Its seven-byte header carries fragment count/index, message ID, channel, direction and little-endian content length. The framing and channel mapping were checked against SCAT's Samsung common parser at commit `646a764eb9fba4e2aa6ba0e847c1fd018853fc17`. This is a separate C implementation, not a dependency on SCAT or Wireshark.

Only downlink channel 0 (DL-CCCH) and 4 (DL-DCCH) are consumed. RRCSetup and RRCReconfiguration provide CellGroupConfig. AddMod and Release lists update per-cell CSI state. Ports follow report -> resource configuration -> non-TRS resource sets -> NZP CSI-RS resources, with the Type I single-panel codebook fallback used by QBM. Ports describe configured CSI-RS resources, not the handset's RX-chain count or scheduled layer count.

Reassembly is bounded to eight in-flight messages, 64 fragments and 65,535 assembled bytes per message. Partial messages expire after five seconds. Exact duplicate fragments are ignored; conflicting duplicates, inconsistent headers and invalid lengths are rejected. No raw RRC messages or subscriber data are logged or retained after decoding. A transport restart clears partial messages and CSI state because missed configuration changes cannot safely be reconstructed.

Serving identities come from the live NR measurements when known, with QBM's unnamed-SpCell fallback for initial configuration. The input is the existing unlabelled modem stream; this does not establish independent raw-stream SIM identification. Display hold instances are separate per attributed Android subscription.

`csi_ports`, `dl_scheduled` and aggregate `rrc_messages`/`rrc_decoded`/`rrc_malformed` counters are added to the existing metrics schema. `NrPortHold` keys NR carriers by PCI/channel and compatible band. A positive-TBS PDSCH window with a reported RX count confirms the current port count, including 1. Idle windows retain the confirmed count, or the largest observed count before confirmation. Disappearing/changing identities clear held state; carrier-slot and PCell/SCell role moves alone preserve it. Shannon's cached RX configuration is not treated as traffic by itself.

The logging profile is unchanged. Missing RRC configuration remains unknown; enable monitoring before the connection is established. This path does not decode LTE-embedded NSA configuration or unsupported NR log/container variants. It can consume configurations delivered through the supported NR RRC stream, including secondaryCellGroup fields in those messages.

## Verification for 0.4.0

Focused host decoding of recorded Shannon RRC and serving/scheduling records produced n41=8 and n28=4 ports, including a PCell/SCell role swap. The ARM64 replay-only executable matched all 181 completed windows of that capture, with 107 assembled downlink messages, 82 complete ASN.1 decodes and no RRC decode failures. Other valid message types are ignored. A second capture produced 119 complete decodes and one rejected ASN.1 message; unsupported or malformed input is not assumed valid. These checks do not establish every RRC revision or logging profile.

The ARM64 helper and isolated APK built successfully. The user confirmed live port counts work after installation. No broad regression suite was run. Source/helper version is 0.4.0; the installed relay remains 0.1.6.

## Bandwidth fallback in 0.4.2

The same decoded `ServingCellConfigCommon.downlinkConfigCommon.frequencyInfoDL`
contains the SSB NR-ARFCN and `scs-SpecificCarrierList`. Each list entry supplies
`subcarrierSpacing` and `carrierBandwidth` (the full carrier grid in resource
blocks). SCell AddMod updates these fields; release/fullConfig/transport reset
clear them along with the existing cell configuration. A SpCell origin change
also clears old carrier-grid widths before naming the new identity.

When a carrier's ordinary channel-configuration bandwidth is unavailable, the
helper queries this configuration using **both PCI and SSB NR-ARFCN**, plus the
live SCS. There is no PCI-only match or unnamed-SpCell fallback for bandwidth.
Conflicting configurations remain unknown. The first implementation covers FR1
and exact NRB/SCS pairs from TS 38.104 Table 5.3.2-1; unsupported values and FR2
remain unknown. It does not use active BWP size, scheduled PRBs, RB rounding,
or a band-only guess. The generated ASN.1 source is unchanged.

Reference: [ETSI TS 138 104 V18.13.0, section 5.3.2](https://www.etsi.org/deliver/etsi_ts/138100_138199/138104/18.13.00_60/ts_138104v181300p.pdf).

The app separately holds the last valid NR bandwidth per Android subscription
and `(SSB NR-ARFCN, PCI)`, allowing compatible band, slot and PCell/SCell changes.
A different known band invalidates a cached value. Missing/zero samples do not
erase the last valid width; any newer valid width replaces it, even if smaller.
The cache retains up to 128 identities per subscription until DIAG is toggled.
Replay records the already-held carrier configuration and seeking cannot update
the live cache. This hold is a last-observed display value, not confirmation that
an absent cell remains configured.

Focused verification (2026-10-28), without writing captures or payloads:

| Capture | Completed windows | Additional SCell BW readings | RRC-only readings matching NSG |
| --- | ---: | ---: | ---: |
| `test_10271955_NR_Measurements` | 181 | 8 | 138 |
| `test_09182104` | 359 | 330 | 330 |
| `test_09181844` | 367 | 0 | 256 |

Replays selected the full received NR RRC stream and serving/channel records
(NR stack-1 IDs 31/35/44/55). Existing known widths and every other output field,
including CSI ports, were unchanged relative to the 0.4.0 host baseline in these
907 windows. A second pass omitted channel records 44/55, forcing RRC-only
widths. The resulting 724 populated carrier windows all agreed with the NSG
JSON's observed widths for the same PCI/SSB NR-ARFCN (n28 20 MHz, n41 90 MHz).
This is an identity-based comparison within each capture; it does not establish
every RRC version, FR1 bandwidth, or independent cold-start logging activation.

`tests/rrc_bandwidth.c` checks exact RB/SCS conversion, identity and SCS mismatches,
ambiguous groups, reconfiguration, reset and SpCell identity change. Kotlin's
`NrBandwidthHoldTest` checks cache matching, invalidation, replacement, per-instance
isolation, temporary absence and bounded retention. Broader traffic regressions
were not run. NSG's own internal bandwidth-source selection was not investigated;
the captures establish that the RRC fallback is useful for SBM.
