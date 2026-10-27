#ifndef SHANNON_TELEMETRY_H
#define SHANNON_TELEMETRY_H
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#define LTE_CELLS 32
#define NR_CELLS 8
#define CHANNELS 32

typedef struct {
    int present;
    int64_t measured_us;
    double band, arfcn, pci, bw_mhz, scs_khz, rx, tx;
    double rsrp, rsrq, rssi, sinr, ssb_sinr, csi_sinr, pdsch_sinr;
    double rank, layers, cqi, mcs_dl, mcs_ul, rb_dl, rb_ul, bler_dl;
    double bler_ul, mod_dl_bits, mod_ul_bits;
    double pusch_dbm, pucch_dbm, beam, tm;
    double chain_rsrp[4], chain_rsrq[4], chain_sinr[4];
    double phy_dl, phy_ul, ul_rb_total;
} Cell;

typedef struct {
    uint64_t dl_bits, ul_bits, rb_sum, rb_count, bler_sum, bler_count;
    int mcs_max;
} Accum;

typedef struct {
    uint32_t arfcn, scs;
    double bw, rx;
    int64_t stamp;
} Channel;

typedef struct Telemetry Telemetry;
typedef void (*snapshot_fn)(Telemetry *, int64_t, void *);
struct Telemetry {
    Cell lte[LTE_CELLS], nr[NR_CELLS];
    Accum accum[NR_CELLS];
    Channel channels[CHANNELS];
    int64_t bucket, origin, now_us, last_lte_us, last_nr_us;
    uint64_t frames, decoded, unsupported, malformed, late, framing_errors;
    uint64_t period_dl_bits;
    int seen_period, ever_dl, ever_ul;
    double phy_dl, phy_ul, lte_dl, lte_ul;
    double l2_dl[3], l2_ul[3];
    int64_t l2_stamp[3];
    snapshot_fn snapshot;
    void *opaque;
};

void telemetry_init(Telemetry *, snapshot_fn, void *);
void telemetry_advance(Telemetry *, int64_t);
void telemetry_decode(Telemetry *, int64_t, unsigned, unsigned, unsigned, const uint8_t *, size_t);
void telemetry_json(Telemetry *, int64_t, void *);
uint16_t read_u16(const uint8_t *);
uint32_t read_u32(const uint8_t *);
#endif
