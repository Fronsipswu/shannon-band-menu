/* Focused configuration-consumer checks; no modem or capture data. */
#include <assert.h>
#include "../rrc/rrc_csi.c"

int main(void) {
    assert(fr1_bandwidth(0, 106) == 20);
    assert(fr1_bandwidth(1, 273) == 100);
    assert(fr1_bandwidth(2, 135) == 100);
    assert(fr1_bandwidth(0, 105) == 0);
    assert(fr1_bandwidth(0, 0) == 0);
    assert(fr1_bandwidth(3, 273) == 0);
    rrc_csi_reset();
    long pci = 100, arfcn = 520000;
    SCS_SpecificCarrier_t carrier = {.subcarrierSpacing = 1, .carrierBandwidth = 273};
    SCS_SpecificCarrier_t *carriers[] = {&carrier};
    FrequencyInfoDL_t frequency = {.absoluteFrequencySSB = &arfcn};
    frequency.scs_SpecificCarrierList.list.array = carriers;
    frequency.scs_SpecificCarrierList.list.count = 1;
    DownlinkConfigCommon_t downlink = {.frequencyInfoDL = &frequency};
    ServingCellConfigCommon_t common = {.physCellId = &pci, .downlinkConfigCommon = &downlink};
    apply_common(&groups[0], 1, &common);
    assert(rrc_csi_bandwidth(100, 520000, 30) == 100);
    assert(rrc_csi_bandwidth(101, 520000, 30) == 0);
    assert(rrc_csi_bandwidth(100, 520001, 30) == 0);
    assert(rrc_csi_bandwidth(100, 520000, 15) == 0);
    assert(rrc_csi_bandwidth(100, 520000, 0) == 0);
    carrier.carrierBandwidth = 217;
    apply_common(&groups[0], 1, &common);
    assert(rrc_csi_bandwidth(100, 520000, 30) == 80);
    groups[1].cell[1] = groups[0].cell[1];
    groups[1].cell[1].dl_rb[1] = 273;
    assert(rrc_csi_bandwidth(100, 520000, 30) == 0); /* ambiguous groups */
    memset(&groups[1], 0, sizeof groups[1]);
    groups[0].cell[1].ssb_arfcn = 0;
    assert(rrc_csi_bandwidth(100, 520000, 30) == 0); /* PCI alone is insufficient */
    apply_common(&groups[0], 1, &common);
    groups[0].spcell = 1;
    struct rrc_origin changed = {.pci = 101, .arfcn = 520000};
    name_spcell(&changed);
    assert(rrc_csi_bandwidth(101, 520000, 30) == 0); /* no width borrowed on handover */
    rrc_csi_reset();
    assert(rrc_csi_bandwidth(100, 520000, 30) == 0);
    return 0;
}
