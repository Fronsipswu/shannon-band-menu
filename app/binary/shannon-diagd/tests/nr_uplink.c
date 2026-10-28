#define _GNU_SOURCE
#include <assert.h>
#include <math.h>
#include <stdlib.h>
#include "../telemetry.c"

static void word(uint8_t *p, unsigned offset, uint32_t value) {
    for (unsigned i = 0; i < 4; i++) p[offset + i] = (uint8_t)(value >> (8 * i));
}

int main(void) {
    Telemetry state;
    telemetry_init(&state, NULL, NULL);
    state.now_us = 1000;
    state.nr[0].present = 1;
    state.nr[0].pci = 100;
    state.nr[0].arfcn = 520000;
    state.nr[0].measured_us = 1000;
    uint8_t debug[52] = {0};
    word(debug, 44, 25);
    assert(decode_nr(&state, 0, 1, debug, sizeof debug) == 1);
    word(debug, 44, 0);
    decode_nr(&state, 0, 1, debug, sizeof debug);
    assert(state.nr[0].mcs_ul == 25);
    word(debug, 44, 28);
    decode_nr(&state, 0, 1, debug, sizeof debug);
    assert(state.nr[0].mcs_ul == 28);
    uint8_t pusch[32] = {0};
    word(pusch, 4, 2u | (6u << 9) | (100u << 23));
    word(pusch, 8, 2u << 25);
    word(pusch, 24, 1000);
    decode_nr(&state, 0, 11, pusch, sizeof pusch);
    word(pusch, 4, (6u << 9) | (100u << 23));
    decode_nr(&state, 0, 11, pusch, sizeof pusch);
    finish(&state, 0);
    assert(state.nr[0].mod_ul_bits == 6);
    assert(state.nr[0].layers_ul == 2);
    assert(state.nr[0].bler_ul == 50);
    word(pusch, 24, 0); /* UCI-only/empty records are not data BLER samples. */
    decode_nr(&state, 0, 11, pusch, sizeof pusch);
    finish(&state, 1);
    assert(isnan(state.nr[0].mod_ul_bits));
    assert(isnan(state.nr[0].layers_ul));
    assert(isnan(state.nr[0].bler_ul));
    state.nr[0].measured_us = state.nr[0].uplink_us = 5000000;
    char *json = NULL;
    size_t size = 0;
    FILE *output = open_memstream(&json, &size);
    telemetry_json(&state, 5, output);
    fclose(output);
    assert(strstr(json, "\"mcs_ul\":null")); /* Old MCS cannot cross an idle gap. */
    free(json);
    word(pusch, 0, 2); /* CC1, independent of the PCell bucket. */
    word(pusch, 24, 1000);
    decode_nr(&state, 0, 11, pusch, sizeof pusch);
    assert(state.accum[0].ul_records == 0 && state.accum[1].ul_records == 1);
    return 0;
}
