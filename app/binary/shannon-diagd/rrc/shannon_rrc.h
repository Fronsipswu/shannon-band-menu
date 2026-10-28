#ifndef SHANNON_RRC_H
#define SHANNON_RRC_H
#include "../telemetry.h"
void shannon_rrc_reset(void);
int shannon_rrc_decode(Telemetry *state, unsigned stack, const uint8_t *data, size_t size);
int shannon_rrc_ports(const Cell *cell, int primary);
int shannon_rrc_bandwidth(const Cell *cell);
#endif
