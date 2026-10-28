/*
 * rrc_csi.c: CSI-RS ports per NR serving cell, from NR RRC reconfigurations.
 *
 * The Shannon transport supplies complete over-the-air RRC messages (UPER). RRCSetup (a new
 * connection: the PCell's first configuration) and reconfigurations are
 * decoded with the generated 38.331 decoder (rrc_nr_gen.c) and applied to a
 * per-cell copy of the CSI measurement configuration, because the network
 * only sends changes: AddMod lists replace whole elements, Release lists
 * remove them (releases first), a SetupRelease release clears the field.
 *
 * Ports are those of the non-zero-power CSI-RS resources a CSI report measures
 * the channel on (report -> resource config -> resource sets -> resources),
 * leaving out TRS sets (tracking only). A report can sit on one serving cell
 * and measure another ("carrier"). Without a resolvable resource, the report's
 * Type I single-panel codebook gives the count (2 x N1 x N2).
 *
 * Cells are numbered per cell group (0 = master, 1 = secondary in NR-DC or
 * when an NSA secondary configuration arrives on its own).
 */
#include <string.h>

#include "rrc_csi.h"
#include "rrc_nr_gen.h"

#define GROUPS 4
#define CELLS 32
#define RESOURCES 192
#define SETS 64
#define RESOURCE_CONFIGS 112
#define REPORTS 48
#define SET_MEMBERS 64
#define CONFIG_SETS 64

struct resource { uint8_t present, ports; };
struct resource_set { uint8_t present, trs, count, member[SET_MEMBERS]; };
struct resource_config { uint8_t present, count, set[CONFIG_SETS]; };
struct report {
    uint8_t present, resource_config, codebook_ports;
    int carrier;               /* serving cell whose resources it measures */
};
struct cell {
    int present;
    int pci;                   /* +1, 0 = not signalled */
    uint32_t ssb_arfcn;        /* +1, 0 = not signalled */
    int band;                  /* 0 = not signalled */
    struct resource resource[RESOURCES];
    struct resource_set set[SETS];
    struct resource_config config[RESOURCE_CONFIGS];
    struct report report[REPORTS];
};
struct group {
    int spcell;                /* SpCell index, -1 before any */
    struct cell cell[CELLS];
};

/* One configuration per SIM (dual SIM); rrc_csi_select picks the current. */
#define SIMS 2
static struct group groups_s[SIMS][GROUPS];
static int initialised_s[SIMS];
static int sim;
#define groups (groups_s[sim])
#define initialised (initialised_s[sim])

void rrc_csi_select(int index) {
    sim = index >= 0 && index < SIMS ? index : 0;
}

static void init_once(void) {
    if (initialised)
        return;
    memset(groups, 0, sizeof groups);
    for (int g = 0; g < GROUPS; g++)
        groups[g].spcell = -1;
    initialised = 1;
}

/* ── Configuration lists ─────────────────────────────────────────────────── */

/* nrofPorts enumeration order: p1, p2, p4, p8, p12, p16, p24, p32. */
static int resource_ports(long nrof_ports) {
    static const uint8_t ports[] = { 1, 2, 4, 8, 12, 16, 24, 32 };
    return nrof_ports >= 0 && nrof_ports < (long)sizeof ports ? ports[nrof_ports] : 0;
}

/* Type I single panel: two ports, or N1 x N2 cross-polarised (2 x N1 x N2).
 * The n1-n2 choice order is two-one, two-two, four-one, three-two, six-one,
 * four-two, eight-one, four-three, six-two, twelve-one, four-four, eight-two,
 * sixteen-one. */
static int codebook_ports(const CodebookConfig_t *codebook) {
    static const uint8_t ports[] = { 4, 8, 8, 12, 12, 16, 16, 24, 24, 24, 32, 32, 32 };
    if (!codebook || codebook->codebookType.present != CodebookConfig__codebookType_PR_type1)
        return 0;
    const struct CodebookConfig__codebookType__type1 *type1 = codebook->codebookType.choice.type1;
    if (!type1 || type1->subType.present != CodebookConfig__codebookType__type1__subType_PR_typeI_SinglePanel)
        return 0;
    const struct CodebookConfig__codebookType__type1__subType__typeI_SinglePanel *panel =
        type1->subType.choice.typeI_SinglePanel;
    if (!panel)
        return 0;
    if (panel->nrOfAntennaPorts.present ==
        CodebookConfig__codebookType__type1__subType__typeI_SinglePanel__nrOfAntennaPorts_PR_two)
        return 2;
    if (!panel->nrOfAntennaPorts.choice.moreThanTwo)
        return 0;
    int layout = panel->nrOfAntennaPorts.choice.moreThanTwo->n1_n2.present;
    return layout >= 1 && layout <= (int)sizeof ports ? ports[layout - 1] : 0;
}

static void apply_resources(struct cell *cell, const CSI_MeasConfig_t *config) {
    if (config->nzp_CSI_RS_ResourceToReleaseList) {
        for (int i = 0; i < config->nzp_CSI_RS_ResourceToReleaseList->list.count; i++) {
            long id = *config->nzp_CSI_RS_ResourceToReleaseList->list.array[i];
            if (id >= 0 && id < RESOURCES)
                memset(&cell->resource[id], 0, sizeof cell->resource[id]);
        }
    }
    if (config->nzp_CSI_RS_ResourceToAddModList) {
        for (int i = 0; i < config->nzp_CSI_RS_ResourceToAddModList->list.count; i++) {
            const NZP_CSI_RS_Resource_t *added = config->nzp_CSI_RS_ResourceToAddModList->list.array[i];
            long id = added->nzp_CSI_RS_ResourceId;
            if (id < 0 || id >= RESOURCES || !added->resourceMapping)
                continue;
            cell->resource[id].present = 1;
            cell->resource[id].ports = (uint8_t)resource_ports(added->resourceMapping->nrofPorts);
        }
    }
}

static void apply_sets(struct cell *cell, const CSI_MeasConfig_t *config) {
    if (config->nzp_CSI_RS_ResourceSetToReleaseList) {
        for (int i = 0; i < config->nzp_CSI_RS_ResourceSetToReleaseList->list.count; i++) {
            long id = *config->nzp_CSI_RS_ResourceSetToReleaseList->list.array[i];
            if (id >= 0 && id < SETS)
                memset(&cell->set[id], 0, sizeof cell->set[id]);
        }
    }
    if (config->nzp_CSI_RS_ResourceSetToAddModList) {
        for (int i = 0; i < config->nzp_CSI_RS_ResourceSetToAddModList->list.count; i++) {
            const NZP_CSI_RS_ResourceSet_t *added = config->nzp_CSI_RS_ResourceSetToAddModList->list.array[i];
            long id = added->nzp_CSI_ResourceSetId;
            if (id < 0 || id >= SETS)
                continue;
            struct resource_set *set = &cell->set[id];
            memset(set, 0, sizeof *set);
            set->present = 1;
            set->trs = added->trs_Info != NULL;
            for (int j = 0; j < added->nzp_CSI_RS_Resources.list.count && set->count < SET_MEMBERS; j++) {
                long member = *added->nzp_CSI_RS_Resources.list.array[j];
                if (member >= 0 && member < RESOURCES)
                    set->member[set->count++] = (uint8_t)member;
            }
        }
    }
}

typedef struct CSI_ResourceConfig__csi_RS_ResourceSetList__nzp_CSI_RS_SSB__nzp_CSI_RS_ResourceSetList
    nzp_set_list;

/* The NZP CSI-RS set list of a resource config, if it has one. */
static const nzp_set_list *nzp_sets(const CSI_ResourceConfig_t *config) {
    if (config->csi_RS_ResourceSetList.present != CSI_ResourceConfig__csi_RS_ResourceSetList_PR_nzp_CSI_RS_SSB
        || !config->csi_RS_ResourceSetList.choice.nzp_CSI_RS_SSB)
        return NULL;
    return config->csi_RS_ResourceSetList.choice.nzp_CSI_RS_SSB->nzp_CSI_RS_ResourceSetList;
}

static void apply_resource_configs(struct cell *cell, const CSI_MeasConfig_t *config) {
    if (config->csi_ResourceConfigToReleaseList) {
        for (int i = 0; i < config->csi_ResourceConfigToReleaseList->list.count; i++) {
            long id = *config->csi_ResourceConfigToReleaseList->list.array[i];
            if (id >= 0 && id < RESOURCE_CONFIGS)
                memset(&cell->config[id], 0, sizeof cell->config[id]);
        }
    }
    if (config->csi_ResourceConfigToAddModList) {
        for (int i = 0; i < config->csi_ResourceConfigToAddModList->list.count; i++) {
            const CSI_ResourceConfig_t *added = config->csi_ResourceConfigToAddModList->list.array[i];
            long id = added->csi_ResourceConfigId;
            if (id < 0 || id >= RESOURCE_CONFIGS)
                continue;
            struct resource_config *stored = &cell->config[id];
            memset(stored, 0, sizeof *stored);
            stored->present = 1;
            const nzp_set_list *sets = nzp_sets(added);
            for (int j = 0; sets && j < sets->list.count && stored->count < CONFIG_SETS; j++) {
                long set = *sets->list.array[j];
                if (set >= 0 && set < SETS)
                    stored->set[stored->count++] = (uint8_t)set;
            }
        }
    }
}

static void apply_reports(struct cell *cell, int cell_index, const CSI_MeasConfig_t *config) {
    if (config->csi_ReportConfigToReleaseList) {
        for (int i = 0; i < config->csi_ReportConfigToReleaseList->list.count; i++) {
            long id = *config->csi_ReportConfigToReleaseList->list.array[i];
            if (id >= 0 && id < REPORTS)
                memset(&cell->report[id], 0, sizeof cell->report[id]);
        }
    }
    if (config->csi_ReportConfigToAddModList) {
        for (int i = 0; i < config->csi_ReportConfigToAddModList->list.count; i++) {
            const CSI_ReportConfig_t *added = config->csi_ReportConfigToAddModList->list.array[i];
            long id = added->reportConfigId;
            if (id < 0 || id >= REPORTS)
                continue;
            struct report *report = &cell->report[id];
            memset(report, 0, sizeof *report);
            report->present = 1;
            report->resource_config = (uint8_t)added->resourcesForChannelMeasurement;
            /* Without "carrier" the report measures the cell it is on. */
            report->carrier = added->carrier ? (int)*added->carrier : cell_index;
            report->codebook_ports = (uint8_t)codebook_ports(added->codebookConfig);
        }
    }
}

static void clear_csi(struct cell *cell) {
    memset(cell->resource, 0, sizeof cell->resource);
    memset(cell->set, 0, sizeof cell->set);
    memset(cell->config, 0, sizeof cell->config);
    memset(cell->report, 0, sizeof cell->report);
}

/* ── Cell groups ─────────────────────────────────────────────────────────── */

static void apply_dedicated(struct group *group, int index, const ServingCellConfig_t *dedicated) {
    if (index < 0 || index >= CELLS || !dedicated)
        return;
    struct cell *cell = &group->cell[index];
    cell->present = 1;
    const struct SetupRelease_CSI_MeasConfig *csi = dedicated->csi_MeasConfig;
    if (!csi)
        return;
    if (csi->present == SetupRelease_CSI_MeasConfig_PR_release) {
        clear_csi(cell);
    } else if (csi->present == SetupRelease_CSI_MeasConfig_PR_setup && csi->choice.setup) {
        apply_resources(cell, csi->choice.setup);
        apply_sets(cell, csi->choice.setup);
        apply_resource_configs(cell, csi->choice.setup);
        apply_reports(cell, index, csi->choice.setup);
    }
}

/* A cell's common configuration names it (PCI, SSB frequency) and its band.
 * Another cell at the same index replaces the old one's state. */
static void apply_common(struct group *group, int index, const ServingCellConfigCommon_t *common) {
    if (index < 0 || index >= CELLS || !common)
        return;
    struct cell *cell = &group->cell[index];
    int pci = common->physCellId ? (int)*common->physCellId + 1 : 0;
    uint32_t ssb = 0;
    int band = 0;
    const FrequencyInfoDL_t *frequency =
        common->downlinkConfigCommon ? common->downlinkConfigCommon->frequencyInfoDL : NULL;
    if (frequency && frequency->absoluteFrequencySSB)
        ssb = (uint32_t)*frequency->absoluteFrequencySSB + 1;
    /* The band explicitly, which an NR-ARFCN alone doesn't always give
     * (n77/n78, n38/n41 overlap). */
    if (frequency && frequency->frequencyBandList && frequency->frequencyBandList->list.count > 0)
        band = (int)*frequency->frequencyBandList->list.array[0];
    int other_cell = (pci && cell->pci && pci != cell->pci) || (ssb && cell->ssb_arfcn && ssb != cell->ssb_arfcn);
    if (other_cell)
        memset(cell, 0, sizeof *cell);
    if (pci)
        cell->pci = pci;
    if (ssb)
        cell->ssb_arfcn = ssb;
    if (band)
        cell->band = band;
    cell->present = 1;
}

static void apply_cell_group(const OCTET_STRING_t *encoded, int secondary) {
    CellGroupConfig_t *config = NULL;
    asn_dec_rval_t result =
        uper_decode_complete(NULL, &asn_DEF_CellGroupConfig, (void **)&config, encoded->buf, encoded->size);
    if (result.code != RC_OK || result.consumed != encoded->size || config->cellGroupId < 0
        || config->cellGroupId >= GROUPS) {
        ASN_STRUCT_FREE(asn_DEF_CellGroupConfig, config);
        return;
    }
    /* A secondary configuration delivered on its own may still say group 0. */
    int group_index = secondary && config->cellGroupId == 0 ? 1 : (int)config->cellGroupId;
    struct group *group = &groups[group_index];

    if (config->spCellConfig) {
        int index = config->spCellConfig->servCellIndex ? (int)*config->spCellConfig->servCellIndex : 0;
        if (index >= 0 && index < CELLS) {
            /* With sync = a new SpCell (handover, PCell change): start over. */
            if (config->spCellConfig->reconfigurationWithSync) {
                memset(&group->cell[index], 0, sizeof group->cell[index]);
                apply_common(group, index, config->spCellConfig->reconfigurationWithSync->spCellConfigCommon);
            }
            group->spcell = index;
            apply_dedicated(group, index, config->spCellConfig->spCellConfigDedicated);
        }
    }
    if (config->sCellToReleaseList) {
        for (int i = 0; i < config->sCellToReleaseList->list.count; i++) {
            long index = *config->sCellToReleaseList->list.array[i];
            if (index >= 0 && index < CELLS)
                memset(&group->cell[index], 0, sizeof group->cell[index]);
        }
    }
    if (config->sCellToAddModList) {
        for (int i = 0; i < config->sCellToAddModList->list.count; i++) {
            const SCellConfig_t *scell = config->sCellToAddModList->list.array[i];
            apply_common(group, (int)scell->sCellIndex, scell->sCellConfigCommon);
            apply_dedicated(group, (int)scell->sCellIndex, scell->sCellConfigDedicated);
        }
    }
    ASN_STRUCT_FREE(asn_DEF_CellGroupConfig, config);
}

static void apply_reconfiguration(const RRCReconfiguration_t *message, int secondary) {
    if (message->criticalExtensions.present != RRCReconfiguration__criticalExtensions_PR_rrcReconfiguration)
        return;
    const RRCReconfiguration_IEs_t *ies = message->criticalExtensions.choice.rrcReconfiguration;
    const RRCReconfiguration_v1530_IEs_t *v1530 = ies->nonCriticalExtension;
    /* fullConfig: everything (or every secondary group) is sent again. */
    if (v1530 && v1530->fullConfig) {
        for (int g = secondary ? 1 : 0; g < GROUPS; g++) {
            memset(&groups[g], 0, sizeof groups[g]);
            groups[g].spcell = -1;
        }
    }
    if (ies->secondaryCellGroup)
        apply_cell_group(ies->secondaryCellGroup, 1);
    if (v1530 && v1530->masterCellGroup)
        apply_cell_group(v1530->masterCellGroup, secondary);
}

/* ── RRC message transport ──────────────────────────────────────────────────────── */

/* The transport can supply the known serving identity, or unknown values. */
struct rrc_origin {
    int pci;
    uint32_t arfcn;
};

void rrc_csi_reset(void) {
    initialised = 0;
    init_once();
}

/* Downlink messages of the master cell group arrive on the PCell, so the
 * record header names it. Renaming the SpCell before each message keeps its
 * identity right even when a handover command was missed (a handover command
 * itself then switches to the target cell's identity). */
static void name_spcell(const struct rrc_origin *record) {
    struct group *master = &groups[0];
    if (record->pci > 1007)       /* no cell in the header */
        return;
    if (master->spcell < 0)
        master->spcell = 0;
    struct cell *pcell = &master->cell[master->spcell];
    pcell->present = 1;
    pcell->pci = record->pci + 1;
    pcell->ssb_arfcn = record->arfcn + 1;
}

/* RRCSetup starts a connection: earlier state is gone, and its master cell
 * group is the PCell's configuration (often with its CSI setup, which later
 * reconfigurations then don't repeat). It configures only the PCell (SCells
 * come later, by reconfiguration) and doesn't name it, but the record header
 * does: it is the cell the message was received on. */
static int decode_dl_ccch(const struct rrc_origin *record, const uint8_t *message, int length) {
    DL_CCCH_Message_t *decoded = NULL;
    asn_dec_rval_t result =
        uper_decode_complete(NULL, &asn_DEF_DL_CCCH_Message, (void **)&decoded, message, (size_t)length);
    if (result.code == RC_OK && result.consumed == (size_t)length
        && decoded->message->present == DL_CCCH_MessageType_PR_c1
        && decoded->message->choice.c1->present == DL_CCCH_MessageType__c1_PR_rrcSetup) {
        const RRCSetup_t *setup = decoded->message->choice.c1->choice.rrcSetup;
        if (setup->criticalExtensions.present == RRCSetup__criticalExtensions_PR_rrcSetup) {
            initialised = 0;
            init_once();
            apply_cell_group(&setup->criticalExtensions.choice.rrcSetup->masterCellGroup, 0);
            name_spcell(record);
        }
    }
    int complete = result.code == RC_OK && result.consumed == (size_t)length;
    ASN_STRUCT_FREE(asn_DEF_DL_CCCH_Message, decoded);
    return complete ? 1 : -1;
}

static int decode_dl_dcch(const struct rrc_origin *record, const uint8_t *message, int length) {
    /* c1 / rrcReconfiguration is the all-zero 5-bit prefix; the reduced
     * decoder only knows that alternative, so check before decoding. */
    if (message[0] & 0xF8)
        return 0;
    DL_DCCH_Message_t *decoded = NULL;
    asn_dec_rval_t result =
        uper_decode_complete(NULL, &asn_DEF_DL_DCCH_Message, (void **)&decoded, message, (size_t)length);
    if (result.code == RC_OK && result.consumed == (size_t)length
        && decoded->message->present == DL_DCCH_MessageType_PR_c1
        && decoded->message->choice.c1->present == DL_DCCH_MessageType__c1_PR_rrcReconfiguration) {
        name_spcell(record);
        apply_reconfiguration(decoded->message->choice.c1->choice.rrcReconfiguration, 0);
    }
    int complete = result.code == RC_OK && result.consumed == (size_t)length;
    ASN_STRUCT_FREE(asn_DEF_DL_DCCH_Message, decoded);
    return complete ? 1 : -1;
}

/* Shannon channel numbers are not Qualcomm B821 PDU numbers. */
int rrc_csi_message(int channel, const uint8_t *message, int length, int pci, uint32_t arfcn) {
    if (!message || length < 1 || length > 65535)
        return -1;
    init_once();
    struct rrc_origin origin = {pci, arfcn};
    if (channel == 0)
        return decode_dl_ccch(&origin, message, length);
    if (channel == 4)
        return decode_dl_dcch(&origin, message, length);
    return 0;
}

/* ── Query ───────────────────────────────────────────────────────────────── */

/* Ports measured on cell `index` of `group`, from reports on any of its cells. */
static int cell_ports(const struct group *group, int index) {
    const struct cell *cell = &group->cell[index];
    int ports = 0;
    int codebook = 0;
    for (int owner = 0; owner < CELLS; owner++) {
        for (int r = 0; r < REPORTS; r++) {
            const struct report *report = &group->cell[owner].report[r];
            if (!report->present || report->carrier != index || report->resource_config >= RESOURCE_CONFIGS)
                continue;
            if (report->codebook_ports > codebook)
                codebook = report->codebook_ports;
            const struct resource_config *config = &cell->config[report->resource_config];
            for (int s = 0; config->present && s < config->count; s++) {
                const struct resource_set *set = &cell->set[config->set[s]];
                if (!set->present || set->trs)
                    continue;
                for (int m = 0; m < set->count; m++) {
                    const struct resource *resource = &cell->resource[set->member[m]];
                    if (resource->present && resource->ports > ports)
                        ports = resource->ports;
                }
            }
        }
    }
    return ports ? ports : codebook;
}

/* The stored cell with this PCI and SSB NR-ARFCN; its group in *group. */
static const struct cell *find_cell(int pci, uint32_t arfcn, int *group, int *index) {
    for (int g = 0; g < GROUPS; g++) {
        for (int c = 0; c < CELLS; c++) {
            const struct cell *cell = &groups[g].cell[c];
            if (!cell->present || cell->pci != pci + 1)
                continue;
            if (cell->ssb_arfcn && cell->ssb_arfcn != arfcn + 1)
                continue;
            *group = g;
            *index = c;
            return cell;
        }
    }
    return NULL;
}

int rrc_csi_band(int pci, uint32_t arfcn) {
    int group;
    int index;
    init_once();
    const struct cell *cell = find_cell(pci, arfcn, &group, &index);
    return cell ? cell->band : 0;
}

int rrc_csi_ports(int pci, uint32_t arfcn, int pcell) {
    int group;
    int index;
    init_once();
    if (find_cell(pci, arfcn, &group, &index))
        return cell_ports(&groups[group], index);
    if (pcell == 0)
        return 0;
    /* No configured cell has this identity, so it is the SpCell whose identity
     * was never signalled (master group first). */
    for (int g = 0; g < GROUPS; g++) {
        int spcell = groups[g].spcell;
        if (spcell >= 0 && !groups[g].cell[spcell].pci) {
            int ports = cell_ports(&groups[g], spcell);
            if (ports)
                return ports;
        }
    }
    return 0;
}
