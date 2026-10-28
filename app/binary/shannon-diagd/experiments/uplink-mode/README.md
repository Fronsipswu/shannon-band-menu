# Disabled uplink mode inference

Preserved from the uncommitted helper 0.4.3 experiment on 2026-10-28 at the
user's request. The active app/helper uses PUSCH Rank 1/2 as of 0.4.4.
These `.c.txt` reference files are deliberately outside the native build list.
They have no effect on the app, helper asset, JSON schema or installed build.

`srs_mode_reference.c.txt` preserves the cell-state additions, RRC SRS/BWP
updates, consistency query and mode conversion. It is an excerpt from the
original consumers, not a standalone translation unit. It depends on the
private `cell`, `groups`, `GROUPS`, `CELLS` and `init_once` definitions in
`rrc/rrc_csi.c`, and on `Cell` and the generated ASN.1 types.

To revisit the experiment, add the saved fields to the RRC cell state, call
`apply_uplink(cell, dedicated->uplinkConfig)` before the CSI-only early return
in `apply_dedicated`, and expose `rrc_csi_ul_ports` in the consumer header.
The mode converter originally lived in `rrc/shannon_rrc.c`. Cell release,
fullConfig and transport reset clear its state through existing cell/group
resets. `name_spcell` must additionally clear both SRS fields on a known
PCI/SSB NR-ARFCN change, alongside the current DL RB-cache reset.

The original helper emitted the result as optional `tx_mode_ul` alongside
`layers_ul`, and the UI used it for a Mode row. Re-enabling that path requires
an explicit product decision and a fresh helper/app handshake version. Do not
replace the current Rank display merely to retain this reference code.

The experiment mapped two PUSCH layers to SMDL. For one layer it required an
exact PCI/SSB NR-ARFCN match and agreement across every configured UL BWP and
SRS resource: one SRS port meant SISO; two/four meant SMSL. Missing or mixed
configuration remained unknown. This is an inference from **logical SRS port
configuration**, not an explicit Shannon TX-mode field or proof of active RF
chains. DCI-driven BWP switches were not recovered.

The original capture replays produced 464 SMDL and one SMSL carrier window;
the NSG exports did not provide an independent SISO/SMSL oracle. The original
synthetic checks covered SISO/SMSL conversion, matching identities, AddMod,
release, conflicting BWPs and reset, and ran on the host and ARM64 phone.
The reference excerpts have not been re-enabled or rebuilt. Capture findings
and the retained uplink metrics are documented in `../../UPLINK-2026-10-28.md`.
