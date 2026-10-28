/* CSI-RS configuration consumer adapted from the public QBM implementation. */
#ifndef SBM_RRC_CSI_H
#define SBM_RRC_CSI_H
#include <stdint.h>
void rrc_csi_select(int index);
void rrc_csi_reset(void);
/* Channels 0=DL-CCCH, 4=DL-DCCH. pci>1007 means no known serving identity.
 * Returns 1 for complete decode, 0 for ignored messages, -1 for failed decode. */
int rrc_csi_message(int channel, const uint8_t *message, int length, int pci, uint32_t arfcn);
int rrc_csi_ports(int pci, uint32_t arfcn, int pcell);
int rrc_csi_band(int pci, uint32_t arfcn);
#endif
