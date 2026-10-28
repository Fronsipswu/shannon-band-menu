#include "../telemetry.h"
#include <assert.h>
#include <math.h>
#include <string.h>

static double dl, ul, qpsk, cqi, success;
static void capture(Telemetry *s, int64_t second, void *opaque) {
    (void)second; (void)opaque;
    dl = s->wcdma.dl_mbps; ul = s->wcdma.ul_mbps;
    qpsk = s->wcdma.modulation_pct[0]; cqi = s->wcdma.cqi;
    success = s->wcdma.scch_pct;
}
static void put32(uint8_t *p, uint32_t v) {
    for (int i = 0; i < 4; i++) p[i] = (uint8_t)(v >> (i * 8));
}
int main(void) {
    Telemetry s;
    telemetry_init(&s, capture, NULL);
    uint8_t down[244] = {0}, up[56] = {0};
    down[4] = 1; down[14] = 100; down[16] = 80;
    down[8] = 5; down[10] = 5;
    down[20 + 30 * 6] = 100;
    put32(down, 10000);
    telemetry_decode(&s, 1000000, 900, 0x1504, 1, down, sizeof(down));
    put32(down, 20000);
    telemetry_decode(&s, 1500000, 900, 0x1504, 1, down, sizeof(down));
    put32(up, 1000); put32(up + 4, 25000);
    telemetry_decode(&s, 1500000, 900, 0x3504, 0, up, sizeof(up));
    telemetry_advance(&s, 2000000);
    /* Average per-report interval rates; two reports are not a full second. */
    assert(fabs(dl - .6) < 1e-9 && fabs(ul - .2) < 1e-9);
    assert(qpsk == 50 && cqi == 30 && success == 80);
    telemetry_advance(&s, 3000000);
    assert(isnan(dl) && isnan(ul) && isnan(cqi));
    down[16] = 101;
    telemetry_decode(&s, 3000000, 900, 0x1504, 1, down, sizeof(down));
    assert(s.malformed == 1);
    down[16] = 80;
    telemetry_decode(&s, 3000000, 900, 0x1504, 1, down, sizeof(down) - 1);
    telemetry_decode(&s, 3000000, 900, 0x1504, 0, down, sizeof(down));
    down[4] = 2;
    telemetry_decode(&s, 3000000, 900, 0x1504, 1, down, sizeof(down));
    assert(s.wcdma.dl_reports == 0 && s.unsupported == 3);
    down[4] = 1;
    telemetry_decode(&s, 3000000, 0, 0x1504, 1, down, sizeof(down));
    assert(s.wcdma.dl_reports == 1); /* live wire format has no ICD tag */
    return 0;
}
