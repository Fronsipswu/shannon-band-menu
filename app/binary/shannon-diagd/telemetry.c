#include "telemetry.h"
#include "rrc/shannon_rrc.h"
#include <inttypes.h>
#include <math.h>
#include <string.h>

uint16_t read_u16(const uint8_t *p) {
    return (uint16_t)(p[0] | ((uint16_t)p[1] << 8));
}
uint32_t read_u32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
static int32_t i32(const uint8_t *p) {
    uint32_t u = read_u32(p);
    int32_t v;
    memcpy(&v, &u, 4);
    return v;
}
static int16_t i16(const uint8_t *p) {
    uint16_t u = read_u16(p);
    int16_t v;
    memcpy(&v, &u, 2);
    return v;
}
static double scaled(const uint8_t *p) {
    return i32(p) / 100.0;
}
static double plausible(double x, double lo, double hi) {
    return x > lo && x < hi ? x : NAN;
}
static int recent(int64_t now, int64_t stamp) {
    return stamp >= 0 && now - stamp < 3000000;
}
static void clear_cell(Cell *c) {
    memset(c, 0, sizeof(*c));
    c->measured_us = -1;
    c->uplink_us = -1;
    c->mcs_ul_us = -1;
    c->band = c->arfcn = c->pci = c->bw_mhz = c->scs_khz = c->rx = c->tx = NAN;
    c->rsrp = c->rsrq = c->rssi = c->sinr = c->ssb_sinr = c->csi_sinr = c->pdsch_sinr = NAN;
    c->rank = c->layers = c->cqi = c->mcs_dl = c->mcs_ul = c->rb_dl = c->rb_ul = c->bler_dl = NAN;
    c->bler_ul = c->mod_dl_bits = c->mod_ul_bits = NAN;
    c->layers_ul = NAN;
    c->pusch_dbm = c->pucch_dbm = c->beam = c->tm = NAN;
    c->phy_dl = c->phy_ul = c->ul_rb_total = NAN;
    for (int j = 0; j < 4; j++)
        c->chain_rsrp[j] = c->chain_rsrq[j] = c->chain_sinr[j] = NAN;
}
static void clear_accum(Telemetry *s) {
    Wcdma *w = &s->wcdma;
    w->dl_sum = w->ul_sum = 0;
    w->dl_reports = w->ul_reports = 0;
    memset(w->modulation, 0, sizeof(w->modulation));
    w->cqi_weight = w->cqi_samples = w->scch_attempts = w->scch_valid = 0;
    memset(s->accum, 0, sizeof(s->accum));
    for (int i = 0; i < NR_CELLS; i++)
        s->accum[i].mcs_max = -1;
    s->period_dl_bits = 0;
    s->seen_period = 0;
}
void telemetry_init(Telemetry *s, snapshot_fn fn, void *opaque) {
    shannon_rrc_reset();
    memset(s, 0, sizeof(*s));
    s->wcdma.measured_us = s->wcdma.signal_us = -1;
    s->wcdma.psc = s->wcdma.rscp = s->wcdma.ecno = NAN;
    s->wcdma.dl_mbps = s->wcdma.ul_mbps = s->wcdma.cqi = s->wcdma.scch_pct = NAN;
    for (int i = 0; i < 3; i++) s->wcdma.modulation_pct[i] = NAN;
    for (int i = 0; i < LTE_CELLS; i++)
        clear_cell(&s->lte[i]);
    for (int i = 0; i < NR_CELLS; i++)
        clear_cell(&s->nr[i]);
    s->bucket = s->origin = s->last_lte_us = s->last_nr_us = -1;
    s->phy_dl = s->phy_ul = s->lte_dl = s->lte_ul = NAN;
    for (int i = 0; i < 3; i++) {
        s->l2_dl[i] = s->l2_ul[i] = NAN;
        s->l2_stamp[i] = -1;
    }
    s->snapshot = fn;
    s->opaque = opaque;
    clear_accum(s);
}
static void finish(Telemetry *s, int64_t second) {
    Wcdma *w = &s->wcdma;
    w->dl_mbps = w->dl_reports ? w->dl_sum / w->dl_reports : NAN;
    w->ul_mbps = w->ul_reports ? w->ul_sum / w->ul_reports : NAN;
    uint64_t mod_total = w->modulation[0] + w->modulation[1] + w->modulation[2];
    for (int i = 0; i < 3; i++)
        w->modulation_pct[i] = mod_total ? 100.0 * w->modulation[i] / mod_total : NAN;
    w->cqi = w->cqi_samples ? (double)w->cqi_weight / w->cqi_samples : NAN;
    w->scch_pct = w->scch_attempts ? 100.0 * w->scch_valid / w->scch_attempts : NAN;
    uint64_t total = 0, ul = 0;
    for (int i = 0; i < NR_CELLS; i++) {
        Accum *a = &s->accum[i];
        Cell *c = &s->nr[i];
        total += a->dl_bits;
        ul += a->ul_bits;
        c->dl_scheduled = a->dl_records > 0;
        c->phy_dl = s->ever_dl ? a->dl_bits / 1e6 : NAN;
        int uplink = recent((second + 1) * 1000000 - 1, c->uplink_us);
        c->phy_ul = uplink ? a->ul_bits / 1e6 : NAN;
        c->bler_ul = a->ul_records ? 100.0 * a->ul_retransmits / a->ul_records : NAN;
        c->mod_ul_bits = a->ul_mod_max ? a->ul_mod_max : NAN;
        c->layers_ul = a->ul_layers_max ? a->ul_layers_max : NAN;
        if (a->rb_count) {
            c->rb_ul = (double)(a->rb_sum / a->rb_count);
            c->ul_rb_total = (double)a->rb_sum;
        } else
            c->rb_ul = c->ul_rb_total = uplink ? 0 : NAN;
        if (a->bler_count)
            c->bler_dl = (double)a->bler_sum / (a->bler_count * 10.0);
        c->rank = a->rank_max ? a->rank_max : NAN;
        c->mcs_dl = a->mcs_max >= 0 ? a->mcs_max : NAN;
        /* Current ID-6 format: capture-confirmed 256-QAM MCS mapping.
         * Retransmission/reserved indices 28..31 are excluded at decode. */
        c->mod_dl_bits = a->mcs_max < 0 ? NAN : a->mcs_max <= 4 ? 2 :
                         a->mcs_max <= 10 ? 4 : a->mcs_max <= 19 ? 6 : 8;
    }
    s->phy_dl = s->ever_dl ? (s->seen_period ? s->period_dl_bits : total) / 1e6 : NAN;
    s->phy_ul = s->ever_ul ? ul / 1e6 : NAN;
    if (s->snapshot)
        s->snapshot(s, second, s->opaque);
    clear_accum(s);
}
void telemetry_advance(Telemetry *s, int64_t us) {
    if (us < 0 || us < s->now_us)
        return;
    int64_t sec = us / 1000000;
    if (s->bucket < 0)
        s->bucket = s->origin = sec;
    else if (sec > s->bucket) {
        finish(s, s->bucket);
        if (sec > s->bucket + 1)
            finish(s, sec - 1);
        s->bucket = sec;
    }
    s->now_us = us;
}
static double frequency(uint32_t a) {
    if (a < 600000)
        return a * 5.0;
    if (a < 2016667)
        return 3000000.0 + (a - 600000) * 15.0;
    return 24250080.0 + (a - 2016667) * 60.0;
}
static void match_channels(Telemetry *s) {
    for (int i = 0; i < NR_CELLS; i++) {
        Cell *c = &s->nr[i];
        if (!c->present || !isfinite(c->arfcn))
            continue;
        double best = 1e100;
        int found = -1, ambiguous = 0;
        for (int j = 0; j < CHANNELS; j++) {
            Channel *ch = &s->channels[j];
            if (!ch->arfcn || (isfinite(c->scs_khz) && c->scs_khz != ch->scs))
                continue;
            double distance = fabs(frequency((uint32_t)c->arfcn) - frequency(ch->arfcn));
            if (distance > ch->bw * 500)
                continue;
            if (distance < best) {
                best = distance;
                found = j;
                ambiguous = 0;
            } else if (distance == best)
                ambiguous = 1;
        }
        if (found >= 0 && !ambiguous) {
            Channel *ch = &s->channels[found];
            c->bw_mhz = ch->bw;
            c->scs_khz = ch->scs;
            c->rx = ch->rx;
        }
    }
}
static void identity(Cell *c, double band, double arfcn, double pci) {
    if (c->present && (c->arfcn != arfcn || c->pci != pci))
        clear_cell(c);
    c->present = 1;
    c->band = band;
    c->arfcn = arfcn;
    c->pci = pci;
}
static double scs(unsigned n) {
    return n < 5 ? (double)(15u << n) : NAN;
}
static int cc_index(const uint8_t *p) {
    unsigned cc = read_u32(p) & 15;
    return (cc & 1) ? -1 : (int)(cc / 2);
}
static int decode_lte(Telemetry *s, const uint8_t *p, size_t n) {
    if (n != 1159)
        return 0;
    unsigned count = p[1];
    if (count > 31)
        return -1;
    if (!p[17] || i16(p + 25) <= -140)
        return 1; /* idle LTE envelope in SA */
    s->last_lte_us = s->now_us;
    s->lte_dl = read_u32(p + 2) / 1000.0;
    s->lte_ul = read_u32(p + 6) / 1000.0;
    static const double bw[] = {1.4, 3, 5, 10, 15, 20};
    for (unsigned i = 0; i <= count; i++) {
        size_t at = i ? 43 + (i - 1) * 36 + 2 : 10;
        Cell *c = &s->lte[i];
        unsigned pci = read_u16(p + at), band = p[at + 7], arfcn = read_u32(p + at + 2);
        if (pci > 503 || band == 0 || p[at + 6] > 5) {
            clear_cell(c);
            continue;
        }
        identity(c, band, arfcn, pci);
        c->bw_mhz = bw[p[at + 6]];
        c->tm = p[at + 14];
        c->rsrp = i16(p + at + 15);
        c->rsrq = i16(p + at + 17);
        c->rssi = i16(p + at + 19);
        c->sinr = i16(p + at + 21);
        c->layers = p[at + 23] ? p[at + 23] : NAN;
        c->rank = c->layers;
        c->cqi = p[at + 24] <= 15 ? p[at + 24] : NAN;
        c->phy_dl = read_u32(p + at + 25) / 1000.0;
        c->phy_ul = read_u32(p + at + 29) / 1000.0;
        if (c->phy_ul > 0)
            c->uplink_us = s->now_us;
        c->measured_us = s->now_us;
    }
    for (unsigned i = count + 1; i < LTE_CELLS; i++)
        clear_cell(&s->lte[i]);
    return 1;
}
static int modulation(unsigned bits) {
    return bits == 1 || bits == 2 || bits == 4 || bits == 6 || bits == 8 || bits == 10;
}
static void lte_grant(Cell *c, const uint8_t *p, int uplink, int64_t now) {
    double bler = read_u32(p) / 100.0;
    if (uplink) {
        c->bler_ul = bler <= 100 ? bler : NAN;
        c->rb_ul = p[8] <= 100 ? p[8] : NAN;
        if (c->rb_ul > 0)
            c->uplink_us = now;
        c->mcs_ul = p[9] <= 31 ? p[9] : NAN;
        c->mod_ul_bits = modulation(p[10]) ? p[10] : NAN;
        c->pusch_dbm = plausible(i16(p + 11) / 100.0, -46, 31);
        c->pucch_dbm = plausible(i16(p + 13) / 100.0, -46, 31);
    } else {
        c->bler_dl = bler <= 100 ? bler : NAN;
        c->rb_dl = p[8] <= 100 ? p[8] : NAN;
        c->mcs_dl = p[9] <= 31 ? p[9] : NAN;
        c->mod_dl_bits = modulation(p[11]) ? p[11] : NAN;
    }
}
static int lte_aux(Telemetry *s, unsigned msg, const uint8_t *p, size_t n) {
    if (msg == 0x1202 && n == 1380) {
        unsigned count = read_u32(p + 43);
        if (count > 31)
            return -1;
        lte_grant(&s->lte[0], p, 0, s->now_us);
        for (unsigned i = 1; i <= count; i++)
            lte_grant(&s->lte[i], p + 47 + (i - 1) * 43, 0, s->now_us);
        return 1;
    }
    if (msg == 0x1302 && n == 1828) {
        lte_grant(&s->lte[0], p, 1, s->now_us);
        /* First SCell start is capture-confirmed; later current-ICD strides
         * differ from old ShannonDM. Do not extrapolate the older stride. */
        unsigned count = read_u32(p + 57);
        if (count > 31)
            return -1;
        if (count)
            lte_grant(&s->lte[1], p + 61, 1, s->now_us);
        return 1;
    }
    if (msg == 0x0602 && n == 836) {
        unsigned count = read_u32(p + 26);
        if (count > 31)
            return -1;
        unsigned tx = read_u32(p + 8), rx = read_u32(p + 12);
        s->lte[0].tx = tx <= 2 ? (double)(1u << tx) : NAN;
        s->lte[0].rx = (rx == 1 || rx == 2 || rx == 4) ? rx : NAN;
        for (unsigned i = 1; i <= count; i++) {
            const uint8_t *r = p + 30 + (i - 1) * 26;
            tx = read_u32(r + 8);
            rx = read_u32(r + 12);
            s->lte[i].tx = (tx == 1 || tx == 2 || tx == 4) ? tx : NAN;
            s->lte[i].rx = (rx == 1 || rx == 2 || rx == 4) ? rx : NAN;
        }
        return 1;
    }
    if (msg == 0x0102 && n == 1732) {
        for (int slot = 0; slot < LTE_CELLS; slot++) {
            Cell *c = &s->lte[slot];
            if (!c->present)
                continue;
            const uint8_t *r = NULL;
            if (slot == 0 && read_u16(p + 4) == (unsigned)c->pci)
                r = p + 6;
            if (slot > 0) {
                int matches = 0;
                for (int j = 0; j < 31; j++) {
                    const uint8_t *candidate = p + 58 + j * 54;
                    if (read_u16(candidate) == (unsigned)c->pci) {
                        r = candidate + 6;
                        matches++;
                    }
                }
                if (matches != 1)
                    r = NULL;
            }
            if (r)
                for (int j = 0; j < 4; j++) {
                    c->chain_rsrp[j] = plausible(read_u32(r + j * 12) / -100.0, -140, -20);
                    c->chain_rsrq[j] = plausible(read_u32(r + j * 12 + 4) / -100.0, -35, 10);
                }
        }
        return 1;
    }
    return 0;
}
static int decode_nr(Telemetry *s, unsigned stack, unsigned id, const uint8_t *p, size_t n) {
    if (stack == 1) {
        if (id == 31 && n == 182) {
            double r = scaled(p + 16), q = scaled(p + 20), v = scaled(p + 24);
            if (!(r > -150 && r < -20 && q > -40 && q < 20 && v > -23 && v < 40))
                return 1;
            unsigned band = read_u32(p), a = read_u32(p + 4), pci = read_u32(p + 12);
            if (!band || band > 261 || !a || a > 3279165 || pci > 1007)
                return -1;
            Cell *c = &s->nr[0];
            identity(c, band, a, pci);
            c->scs_khz = scs(read_u32(p + 8));
            c->rsrp = r;
            c->rsrq = q;
            c->sinr = v;
            c->rssi = plausible(scaled(p + 28), -150, 0);
            c->beam = p[32];
            for (int i = 0; i < 4; i++) {
                c->chain_rsrp[i] = plausible(scaled(p + 37 + 4 * i), -140, -20);
                c->chain_rsrq[i] = plausible(scaled(p + 53 + 4 * i), -35, 20);
            }
            c->measured_us = s->last_nr_us = s->now_us;
            match_channels(s);
            return 1;
        }
        if (id == 35 && n == 1264) {
            unsigned count = read_u32(p);
            if (count > 7)
                return -1;
            for (unsigned i = 1; i < NR_CELLS; i++) {
                if (i > count) {
                    clear_cell(&s->nr[i]);
                    continue;
                }
                const uint8_t *r = p + 4 + (i - 1) * 180;
                unsigned a = read_u32(r + 2), pci = read_u32(r + 10), band = read_u16(r + 14);
                double rsrp = scaled(r + 20);
                if (!a || a > 3279165 || pci > 1007 || !band || band > 261 || rsrp <= -150 ||
                    rsrp >= -20) {
                    clear_cell(&s->nr[i]);
                    continue;
                }
                Cell *c = &s->nr[i];
                identity(c, band, a, pci);
                c->scs_khz = scs(read_u32(r + 6));
                c->rsrp = rsrp;
                c->rsrq = scaled(r + 24);
                c->sinr = scaled(r + 28);
                c->rssi = scaled(r + 32);
                c->measured_us = s->now_us;
            }
            match_channels(s);
            return 1;
        }
        if ((id == 44 || id == 55) && n == 72) {
            unsigned bw = read_u32(p), spacing = read_u32(p + 12), rx = read_u32(p + 16),
                     a = read_u32(p + 24);
            if (bw < 5 || bw > 400 || !a || a > 3279165 ||
                !(spacing == 15 || spacing == 30 || spacing == 60 || spacing == 120 ||
                  spacing == 240))
                return -1;
            int slot = -1;
            int64_t oldest = INT64_MAX;
            for (int j = 0; j < CHANNELS; j++) {
                if (s->channels[j].arfcn == a || !s->channels[j].arfcn) {
                    slot = j;
                    break;
                }
                if (s->channels[j].stamp < oldest) {
                    oldest = s->channels[j].stamp;
                    slot = j;
                }
            }
            double rxn = rx == 0 ? 0 : rx == 1 ? 1 : rx == 3 ? 2 : rx == 15 ? 4 : NAN;
            s->channels[slot] = (Channel){a, spacing, bw, rxn, s->now_us};
            match_channels(s);
            return 1;
        }
        if ((id == 13 && n == 304) || ((id == 14 || id == 15) && n == 8)) {
            int idx = (int)id - 13;
            s->l2_ul[idx] = read_u32(p) / 1000.0;
            s->l2_dl[idx] = read_u32(p + 4) / 1000.0;
            s->l2_stamp[idx] = s->now_us;
            return 1;
        }
        return 0;
    }
    if (stack != 0)
        return 0;
    if ((id == 8 || id == 9) && n == 16) {
        int slot = cc_index(p);
        if (slot < 0)
            return 1;
        double best = -129;
        for (int j = 0; j < 4; j++) {
            int8_t v;
            memcpy(&v, p + 8 + j, 1);
            if (v > best)
                best = v;
        }
        if (best >= -23 && best <= 40) {
            if (id == 8)
                s->nr[slot].ssb_sinr = best;
            else
                s->nr[slot].pdsch_sinr = best;
        }
        return 1;
    }
    if (id == 10 && n == 40) {
        int slot = cc_index(p);
        if (slot < 0)
            return 1;
        int allzero = 1;
        double best = -129;
        for (int j = 0; j < 4; j++) {
            int8_t v;
            memcpy(&v, p + 8 + j, 1);
            if (v)
                allzero = 0;
            if (v > best)
                best = v;
        }
        if (!allzero && best >= -23 && best <= 40)
            s->nr[slot].csi_sinr = best;
        return 1;
    }
    if (id == 29 && n == 4) {
        int rank = (int)(read_u32(p) & 3) + 1;
        if (rank > s->accum[0].rank_max)
            s->accum[0].rank_max = rank;
        s->nr[0].layers = ((read_u32(p) >> 2) & 3) + 1;
        return 1;
    }
    if (id == 1 && n == 52) {
        int slot = cc_index(p);
        if (slot < 0)
            return 1;
        Cell *c = &s->nr[slot];
        int cq = i32(p + 16);
        unsigned rank = read_u32(p + 12) + 1, layer = read_u32(p + 48);
        if (cq >= 1 && cq <= 15)
            c->cqi = cq;
        if (rank >= 1 && rank <= 4 && (int)rank > s->accum[slot].rank_max)
            s->accum[slot].rank_max = (int)rank;
        if (layer >= 1 && layer <= 4)
            c->layers = layer;
        int power = i16(p + 24);
        if (power >= -45 && power <= 30)
            c->pusch_dbm = power;
        power = i16(p + 28);
        if (power >= -45 && power <= 30)
            c->pucch_dbm = power;
        unsigned mcs = read_u32(p + 44);
        /* Zero in this periodic debug field is also used for no MCS report.
         * Keep a recent positive report instead of overwriting it with zero. */
        if (mcs >= 1 && mcs <= 31) {
            c->mcs_ul = mcs;
            c->mcs_ul_us = s->now_us;
        }
        return 1;
    }
    if (id == 6 && n == 48) {
        int slot = cc_index(p);
        if (slot < 0)
            return 1;
        Accum *a = &s->accum[slot];
        a->dl_bits += read_u32(p + 12);
        if (read_u32(p + 12) > 0)
            a->dl_records++;
        a->bler_sum += read_u32(p + 44);
        a->bler_count++;
        unsigned mcs = read_u32(p + 36);
        if (mcs <= 27 && (int)mcs > a->mcs_max)
            a->mcs_max = (int)mcs;
        s->ever_dl = 1;
        return 1;
    }
    if (id == 7 && n == 12) {
        s->period_dl_bits += read_u32(p + 8);
        s->seen_period = s->ever_dl = 1;
        return 1;
    }
    if (id == 11 && n == 32) {
        int slot = cc_index(p);
        if (slot < 0)
            return 1;
        Accum *a = &s->accum[slot];
        unsigned tbs = read_u32(p + 24);
        a->ul_bits += tbs;
        if (tbs > 0) {
            unsigned control = read_u32(p + 4), timing = read_u32(p + 8);
            unsigned mod = (control >> 9) & 31, layers = (timing >> 25) & 7;
            a->ul_records++;
            if (!(control & 2))
                a->ul_retransmits++;
            if ((mod == 2 || mod == 4 || mod == 6 || mod == 8) && (int)mod > a->ul_mod_max)
                a->ul_mod_max = (int)mod;
            if (layers >= 1 && layers <= 4 && (int)layers > a->ul_layers_max)
                a->ul_layers_max = (int)layers;
        }
        s->ever_ul = 1;
        unsigned rb = (read_u32(p + 4) >> 23) & 511;
        if (read_u32(p + 24) > 0 || (rb >= 1 && rb <= 273))
            s->nr[slot].uplink_us = s->now_us;
        if (rb >= 1 && rb <= 273) {
            a->rb_sum += rb;
            a->rb_count++;
        }
        return 1;
    }
    if (id == 25 && n == 28) {
        unsigned cc = read_u32(p + 24), rb = read_u32(p + 16);
        if (cc < 16 && !(cc & 1) && rb > 0 && rb <= 273)
            s->nr[cc / 2].rb_dl = rb;
        return 1;
    }
    return 0;
}
/* ICD 9 HSPA single-carrier formats, checked against the HSPA+ NSG export.
 * Byte counts are interval totals, not cumulative counters. */
static int decode_wcdma(Telemetry *s, unsigned msg, const uint8_t *p, size_t n) {
    Wcdma *w = &s->wcdma;
    if (msg == 0x1504) {
        if (n < 244 || p[4] != 1 || read_u16(p + 14) != 100) return 0;
        unsigned valid = read_u16(p + 16);
        if (valid > 100) return -1;
        w->dl_sum += read_u32(p) * 40.0 / 1e6; /* 200 ms = 100 two-ms TTIs. */
        w->dl_reports++;
        for (int i = 0; i < 3; i++) w->modulation[i] += read_u16(p + 8 + i * 2);
        w->scch_attempts += 100;
        w->scch_valid += valid;
        for (unsigned cqi = 0; cqi <= 30; cqi++) {
            const uint8_t *entry = p + 20 + cqi * 6;
            unsigned count = read_u16(entry) + read_u16(entry + 2) + read_u16(entry + 4);
            w->cqi_weight += cqi * count;
            w->cqi_samples += count;
        }
        w->measured_us = s->now_us;
        return 1;
    }
    if (msg == 0x3504) {
        if (n < 56 || read_u32(p) != 1000) return 0;
        w->ul_sum += read_u32(p + 4) * 8.0 / 1e6;
        w->ul_reports++;
        w->measured_us = s->now_us;
        return 1;
    }
    if (msg == 0x1b04 && n == 10) {
        unsigned psc = read_u16(p);
        int rscp = i16(p + 2), ecno = i16(p + 6);
        if (psc > 511 || rscp < -120 || rscp > -20 || ecno < -24 || ecno > 0) return 0;
        w->psc = psc;
        w->rscp = rscp;
        w->ecno = ecno;
        w->signal_us = w->measured_us = s->now_us;
        return 1;
    }
    return 0;
}

void telemetry_decode(Telemetry *s, int64_t us, unsigned icd, unsigned msg, unsigned stack,
                      const uint8_t *p, size_t n) {
    s->frames++;
    if (s->bucket >= 0 && us / 1000000 < s->bucket) {
        s->late++;
        return;
    }
    telemetry_advance(s, us);
    if (icd && icd != 900) {
        s->unsupported++;
        return;
    }
    int result = 0;
    if (msg == 0x0801)
        result = shannon_rrc_decode(s, stack, p, n);
    else if (msg == 0x1902)
        result = decode_lte(s, p, n);
    else if (msg == 0xf101 && n >= 4)
        result = decode_nr(s, stack, read_u32(p) & 65535, p + 4, n - 4);
    /* Raw device frames have no ICD envelope. They must pass the same
     * captured layout signatures as ICD-9 replay records. */
    else if ((icd == 0 || icd == 900) && (((msg == 0x1504 || msg == 0x1b04) && stack == 1) ||
                            (msg == 0x3504 && stack == 0)))
        result = decode_wcdma(s, msg, p, n);
    else
        result = lte_aux(s, msg, p, n);
    if (result > 0)
        s->decoded++;
    else if (result < 0)
        s->malformed++;
    else
        s->unsupported++;
}

static void number(FILE *f, const char *name, double v, int comma) {
    fprintf(f, "%s\"%s\":", comma ? "," : "", name);
    if (isfinite(v))
        fprintf(f, "%.6f", v);
    else
        fputs("null", f);
}
static void array(FILE *f, const char *name, double *v) {
    fprintf(f, ",\"%s\":[", name);
    for (int i = 0; i < 4; i++) {
        if (i)
            fputc(',', f);
        if (isfinite(v[i]))
            fprintf(f, "%.2f", v[i]);
        else
            fputs("null", f);
    }
    fputc(']', f);
}
static void cells(FILE *f, Cell *cells, int n, int64_t now, int nr) {
    fputc('[', f);
    int first = 1;
    for (int i = 0; i < n; i++) {
        Cell *c = &cells[i];
        if (!c->present)
            continue;
        fprintf(f, "%s{\"slot\":%d,\"role\":\"%s\",\"fresh\":%s", first ? "" : ",", i,
                i ? "scell" : "pcell", recent(now, c->measured_us) ? "true" : "false");
        first = 0;
        fprintf(f, ",\"uplink_active\":%s", recent(now, c->uplink_us) ? "true" : "false");
        if (nr) {
            int ports = shannon_rrc_ports(c, i == 0);
            number(f, "csi_ports", ports ? ports : NAN, 1);
            fprintf(f, ",\"dl_scheduled\":%s", c->dl_scheduled ? "true" : "false");
        }
        number(f, "band", c->band, 1);
        number(f, "arfcn", c->arfcn, 1);
        number(f, "pci", c->pci, 1);
        double bandwidth = c->bw_mhz;
        if (nr && !isfinite(bandwidth)) {
            int configured = shannon_rrc_bandwidth(c);
            if (configured > 0)
                bandwidth = configured;
        }
        number(f, "bandwidth_mhz", bandwidth, 1);
        number(f, "scs_khz", c->scs_khz, 1);
        number(f, "rx", c->rx, 1);
        number(f, "tx", c->tx, 1);
        int fresh = recent(now, c->measured_us);
        number(f, "rsrp_dbm", fresh ? c->rsrp : NAN, 1);
        number(f, "rsrq_db", fresh ? c->rsrq : NAN, 1);
        number(f, "sinr_db", fresh ? c->sinr : NAN, 1);
        number(f, "rssi_dbm", fresh ? c->rssi : NAN, 1);
        number(f, "ssb_sinr_db", fresh ? c->ssb_sinr : NAN, 1);
        number(f, "csi_sinr_db", fresh ? c->csi_sinr : NAN, 1);
        number(f, "pdsch_sinr_db", fresh ? c->pdsch_sinr : NAN, 1);
        number(f, "cqi", fresh ? c->cqi : NAN, 1);
        number(f, "rank", fresh ? c->rank : NAN, 1);
        number(f, "layers", fresh ? c->layers : NAN, 1);
        number(f, "mcs_dl", fresh ? c->mcs_dl : NAN, 1);
        int ul_mcs_fresh = !nr || (recent(now, c->mcs_ul_us) && recent(now, c->uplink_us));
        number(f, "mcs_ul", fresh && ul_mcs_fresh ? c->mcs_ul : NAN, 1);
        if (nr) {
            number(f, "layers_ul", c->layers_ul, 1);
        }
        number(f, "prbs_dl", fresh ? c->rb_dl : NAN, 1);
        number(f, nr ? "prbs_ul_avg" : "prbs_ul_grant", c->rb_ul, 1);
        number(f, "prbs_ul_total", c->ul_rb_total, 1);
        number(f, "bler_ul_pct", fresh ? c->bler_ul : NAN, 1);
        number(f, "modulation_dl_bits", c->mod_dl_bits, 1);
        number(f, "modulation_ul_bits", c->mod_ul_bits, 1);
        number(f, "bler_dl_pct", fresh ? c->bler_dl : NAN, 1);
        number(f, "phy_dl_mbps", c->phy_dl, 1);
        number(f, "phy_ul_mbps", c->phy_ul, 1);
        number(f, "pusch_dbm", fresh ? c->pusch_dbm : NAN, 1);
        number(f, "pucch_dbm", fresh ? c->pucch_dbm : NAN, 1);
        number(f, "beam", fresh ? c->beam : NAN, 1);
        number(f, "transmission_mode", fresh ? c->tm : NAN, 1);
        if (fresh) {
            array(f, "chain_rsrp_dbm", c->chain_rsrp);
            array(f, "chain_rsrq_db", c->chain_rsrq);
        }
        fputc('}', f);
    }
    fputc(']', f);
}
void telemetry_json(Telemetry *s, int64_t sec, void *opaque) {
    FILE *f = opaque ? opaque : stdout;
    int64_t now = (sec + 1) * 1000000 - 1;
    int lte = recent(now, s->last_lte_us), nr = recent(now, s->last_nr_us);
    int umts = recent(now, s->wcdma.measured_us) && !lte && !nr;
    fprintf(f,
            "{\"schema\":1,\"window_s\":%" PRId64 ",\"rat\":\"%s\",\"frames\":%" PRIu64
            ",\"decoded\":%" PRIu64 ",\"unsupported\":%" PRIu64 ",\"malformed\":%" PRIu64
            ",\"late\":%" PRIu64 ",\"framing_errors\":%" PRIu64
            ",\"rrc_messages\":%" PRIu64 ",\"rrc_decoded\":%" PRIu64
            ",\"rrc_malformed\":%" PRIu64 ",\"lte\":{",
            sec - s->origin, nr ? (lte ? "NSA" : "SA") : (lte ? "LTE" : umts ? "WCDMA" : "idle"), s->frames,
            s->decoded, s->unsupported, s->malformed, s->late, s->framing_errors,
            s->rrc_messages, s->rrc_decoded, s->rrc_malformed);
    number(f, "dl_mbps", lte ? s->lte_dl : NAN, 0);
    number(f, "ul_mbps", lte ? s->lte_ul : NAN, 1);
    fputs(",\"cells\":", f);
    cells(f, s->lte, LTE_CELLS, now, 0);
    fputs("},\"nr\":{", f);
    number(f, "phy_dl_mbps", s->phy_dl, 0);
    number(f, "phy_ul_mbps", s->phy_ul, 1);
    static const char *dl[] = {"mac_dl_mbps", "rlc_dl_mbps", "pdcp_dl_mbps"};
    static const char *ul[] = {"mac_ul_mbps", "rlc_ul_mbps", "pdcp_ul_mbps"};
    for (int i = 0; i < 3; i++) {
        int fresh = recent(now, s->l2_stamp[i]);
        number(f, dl[i], fresh ? s->l2_dl[i] : NAN, 1);
        number(f, ul[i], fresh ? s->l2_ul[i] : NAN, 1);
    }
    fputs(",\"cells\":", f);
    cells(f, s->nr, NR_CELLS, now, 1);
    fputs("},\"wcdma\":{", f);
    Wcdma *w = &s->wcdma;
    int signal = umts && recent(now, w->signal_us);
    number(f, "dl_mbps", umts ? w->dl_mbps : NAN, 0);
    number(f, "ul_mbps", umts ? w->ul_mbps : NAN, 1);
    number(f, "psc", signal ? w->psc : NAN, 1);
    number(f, "cpich_rscp_dbm", signal ? w->rscp : NAN, 1);
    number(f, "cpich_ecno_db", signal ? w->ecno : NAN, 1);
    number(f, "cqi", umts ? w->cqi : NAN, 1);
    number(f, "qpsk_pct", umts ? w->modulation_pct[0] : NAN, 1);
    number(f, "qam16_pct", umts ? w->modulation_pct[1] : NAN, 1);
    number(f, "qam64_pct", umts ? w->modulation_pct[2] : NAN, 1);
    number(f, "hs_scch_success_pct", umts ? w->scch_pct : NAN, 1);
    fputs("}}\n", f);
    if (fflush(f) == EOF) { /* caller checks stdout failure and exits cleanly */
    }
}
