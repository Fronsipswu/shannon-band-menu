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
