/* Shannon 0x0801 NR RRC framing. No raw messages are retained or logged. */
#include "shannon_rrc.h"
#include "rrc_csi.h"
#include <math.h>
#include <stdlib.h>
#include <string.h>

#define ASSEMBLIES 8
#define FRAGMENTS 64
#define MESSAGE_BYTES 65535
#define FRAGMENT_LIFETIME_US 5000000

typedef struct {
    uint8_t *data;
    size_t size;
} Fragment;

typedef struct {
    int used;
    unsigned tag, stack, count, channel;
    int64_t started;
    size_t bytes;
    int pci;
    uint32_t arfcn;
    Fragment parts[FRAGMENTS];
} Assembly;

static Assembly assemblies[ASSEMBLIES];

static void clear(Assembly *assembly) {
    for (unsigned i = 0; i < FRAGMENTS; i++)
        free(assembly->parts[i].data);
    memset(assembly, 0, sizeof(*assembly));
}

void shannon_rrc_reset(void) {
    for (unsigned i = 0; i < ASSEMBLIES; i++)
        clear(&assemblies[i]);
    rrc_csi_select(0); /* One unlabelled modem stream, not a guessed SIM. */
    rrc_csi_reset();
}

static int message(Telemetry *state, unsigned channel, const uint8_t *data, size_t size,
                   int pci, uint32_t arfcn) {
    state->rrc_messages++;
    int result = rrc_csi_message((int)channel, data, (int)size, pci, arfcn);
    if (result > 0)
        state->rrc_decoded++;
    else if (result < 0)
        state->rrc_malformed++;
    return result < 0 ? -1 : 1;
}

int shannon_rrc_ports(const Cell *cell, int primary) {
    if (!cell->present || !isfinite(cell->pci) || !isfinite(cell->arfcn))
        return 0;
    return rrc_csi_ports((int)cell->pci, (uint32_t)cell->arfcn, primary);
}

int shannon_rrc_bandwidth(const Cell *cell) {
    if (!cell->present || !isfinite(cell->pci) || !isfinite(cell->arfcn) || !isfinite(cell->scs_khz))
        return 0;
    return rrc_csi_bandwidth((int)cell->pci, (uint32_t)cell->arfcn, (int)cell->scs_khz);
}

int shannon_rrc_decode(Telemetry *state, unsigned stack, const uint8_t *data, size_t size) {
    /* Current ICD 9.0 captures use stack 1 for this record. Other variants
     * need independent framing evidence rather than sharing assembly state. */
    if (stack != 1)
        return 0;
    if (size < 7)
        return -1;
    unsigned count = data[0], index = data[1], tag = data[2], channel = data[3];
    unsigned direction = data[4];
    size_t bytes = read_u16(data + 5);
    if (direction != 0 || (channel != 0 && channel != 4))
        return 0; /* No uplink, broadcast or NAS decoding. */
    if (!count || count > FRAGMENTS || bytes != size - 7 || !bytes || bytes > MESSAGE_BYTES)
        return -1;
    const Cell *primary = &state->nr[0];
    int known = primary->present && isfinite(primary->pci) && isfinite(primary->arfcn) &&
                primary->measured_us >= 0 && state->now_us - primary->measured_us < 3000000;
    int pci = known ? (int)primary->pci : 1008;
    uint32_t arfcn = known ? (uint32_t)primary->arfcn : 0;
    for (unsigned i = 0; i < ASSEMBLIES; i++)
        if (assemblies[i].used && state->now_us - assemblies[i].started >= FRAGMENT_LIFETIME_US)
            clear(&assemblies[i]);
    if (count == 1) {
        if (index > 1)
            return -1;
        for (unsigned i = 0; i < ASSEMBLIES; i++)
            if (assemblies[i].used && assemblies[i].tag == tag && assemblies[i].stack == stack)
                clear(&assemblies[i]);
        return message(state, channel, data + 7, bytes, pci, arfcn);
    }
    if (!index || index > count)
        return -1;
    Assembly *assembly = NULL, *available = NULL;
    for (unsigned i = 0; i < ASSEMBLIES; i++) {
        Assembly *candidate = &assemblies[i];
        if (candidate->used && candidate->tag == tag && candidate->stack == stack)
            assembly = candidate;
        if (!candidate->used)
            available = candidate;
    }
    if (assembly && (assembly->count != count || assembly->channel != channel)) {
        clear(assembly);
        return -1;
    }
    if (!assembly) {
        if (!available)
            return -1;
        assembly = available;
        assembly->used = 1;
        assembly->tag = tag;
        assembly->stack = stack;
        assembly->count = count;
        assembly->channel = channel;
        assembly->started = state->now_us;
        assembly->pci = pci;
        assembly->arfcn = arfcn;
    }
    Fragment *part = &assembly->parts[index - 1];
    if (part->data) {
        if (part->size == bytes && !memcmp(part->data, data + 7, bytes))
            return 1;
        clear(assembly); /* Conflicting duplicates must never combine messages. */
        return -1;
    }
    if (bytes > MESSAGE_BYTES - assembly->bytes) {
        clear(assembly);
        return -1;
    }
    part->data = malloc(bytes);
    if (!part->data) {
        clear(assembly);
        return -1;
    }
    memcpy(part->data, data + 7, bytes);
    part->size = bytes;
    assembly->bytes += bytes;
    for (unsigned i = 0; i < count; i++)
        if (!assembly->parts[i].data)
            return 1;
    uint8_t *joined = malloc(assembly->bytes);
    if (!joined) {
        clear(assembly);
        return -1;
    }
    size_t at = 0;
    for (unsigned i = 0; i < count; i++) {
        memcpy(joined + at, assembly->parts[i].data, assembly->parts[i].size);
        at += assembly->parts[i].size;
    }
    int result = message(state, channel, joined, assembly->bytes, assembly->pci, assembly->arfcn);
    free(joined);
    clear(assembly);
    return result;
}
