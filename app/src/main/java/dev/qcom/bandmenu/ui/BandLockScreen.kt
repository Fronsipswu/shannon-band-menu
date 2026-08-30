package dev.qcom.bandmenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.qcom.bandmenu.BandConstants
import dev.qcom.bandmenu.BandProfileResolver
import dev.qcom.bandmenu.HardwareBands
import dev.qcom.bandmenu.ModemState
import dev.qcom.bandmenu.NrMode
import dev.qcom.bandmenu.RatType
import dev.qcom.bandmenu.SimState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

private enum class BandFamily { GSM, WCDMA, LTE, NR, NR_NSA, NR_SA }

private class SlotBandState {
    val ratChecked = mutableStateMapOf<RatType, Boolean>()
    val gsmChecked = mutableStateMapOf<Int, Boolean>()
    val wcdmaChecked = mutableStateMapOf<Int, Boolean>()
    val lteChecked = mutableStateMapOf<Int, Boolean>()
    val nrNsaChecked = mutableStateMapOf<Int, Boolean>()
    val nrSaChecked = mutableStateMapOf<Int, Boolean>()
    val nrChecked = mutableStateMapOf<Int, Boolean>()
    val editedBandFamilies = mutableStateMapOf<BandFamily, Boolean>()
    var nrMode by mutableStateOf(NrMode.BOTH)
    var lastEnabledNrMode by mutableStateOf(NrMode.BOTH)
}

/** NR is switched off through the mode NV, so DISABLE/UNKNOWN is never a mode to restore. */
private fun NrMode.enabledOrBoth(): NrMode =
    if (this == NrMode.DISABLE || this == NrMode.UNKNOWN) NrMode.BOTH else this

@Composable
fun BandLockScreen(
    modemState: ModemState?,
    desiredProfile: SimState?,
    isLoading: Boolean,
    refreshingSlots: Set<Int>,
    onRefresh: (Int) -> Unit,
    refreshKey0: Int,
    refreshKey1: Int,
    onApply: (Int, SimState, SimState) -> Unit,
    onReset: (Int) -> Unit,
    onModeChange: (Int, NrMode) -> Unit = { _, _ -> },
    nrIndependentSupported: Boolean? = null,
    visibleLteBands: Set<Int>? = null,
    visibleNrBands: Set<Int>? = null,
    snackbarHostState: SnackbarHostState,
    backdrop: Backdrop? = null
) {
    val density = LocalDensity.current
    val navbarHeightDp = 64.dp
    val navInset = WindowInsets.navigationBars.asPaddingValues(density).calculateBottomPadding()
    val navbarSpace = navbarHeightDp + 16.dp + navInset
    val applyResetSpace = 72.dp
    val statusBarInset = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding()
    val topBarHeight = statusBarInset + TopAppBarDefaults.CollapsedHeight

    val hapticFeedback = LocalHapticFeedback.current

    val hardware = modemState?.hardware
    val useIndependentLock = nrIndependentSupported == true

    var selectedSim by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val slotStates = remember { arrayOf(SlotBandState(), SlotBandState()) }

    LaunchedEffect(selectedSim) {
        if (pagerState.targetPage != selectedSim) {
            pagerState.animateScrollToPage(selectedSim)
        }
    }
    LaunchedEffect(pagerState.targetPage) {
        selectedSim = pagerState.targetPage
    }

    // SmallTopAppBar always applies the top system-bar inset itself. Applying
    // the scaffold's top padding here would count that inset twice.
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading || hardware == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val displayHardware = HardwareBands(
                gsm = hardware.gsm,
                wcdma = hardware.wcdma,
                lte = (visibleLteBands ?: hardware.lte).intersect(hardware.lte),
                nr = (visibleNrBands ?: hardware.nr).intersect(hardware.nr)
            )

            fun applyNow() {
                val s = slotStates[selectedSim]
                val isNrActive = s.ratChecked[RatType.NR] == true
                val isLteActive = s.ratChecked[RatType.LTE] == true
                val isWcdmaActive = s.ratChecked[RatType.WCDMA] == true
                val isGsmActive = s.ratChecked[RatType.GSM] == true
                val isNrOnly = isNrActive && !isLteActive && !isWcdmaActive && !isGsmActive

                // NR-only has no LTE anchor. Normalize its mode when the user
                // explicitly applies, rather than while editing the RAT menu.
                if (isNrOnly && s.nrMode != NrMode.BOTH) {
                    s.nrMode = NrMode.BOTH
                    s.lastEnabledNrMode = NrMode.BOTH
                }

                val nrBands = if (isNrActive) s.nrChecked.filterValues { it }.keys.intersect(displayHardware.nr) else emptySet()
                val selectedNrNsa = s.nrNsaChecked.filterValues { it }.keys.intersect(displayHardware.nr)
                val selectedNrSa = s.nrSaChecked.filterValues { it }.keys.intersect(displayHardware.nr)
                // A mode-inactive family is intentionally rendered empty. If
                // NR-only Apply activates both families, restore that family's
                // internal profile instead of submitting/persisting the empty UI.
                val effectiveNrNsa = if (isNrOnly && selectedNrNsa.isEmpty()) {
                    desiredProfile?.nrNsaBands?.intersect(displayHardware.nr).orEmpty()
                } else selectedNrNsa
                val effectiveNrSa = if (isNrOnly && selectedNrSa.isEmpty()) {
                    desiredProfile?.nrSaBands?.intersect(displayHardware.nr).orEmpty()
                } else selectedNrSa
                val nrNsaBands = if (isNrActive) (if (useIndependentLock) effectiveNrNsa else nrBands) else emptySet()
                val nrSaBands = if (isNrActive) (if (useIndependentLock) effectiveNrSa else nrBands) else emptySet()
                val lteBands = if (isLteActive) s.lteChecked.filterValues { it }.keys.intersect(displayHardware.lte) else emptySet()
                val wcdmaBands = if (isWcdmaActive) {
                    val checked = s.wcdmaChecked.filterValues { it }.keys.intersect(hardware.wcdma)
                    if (checked.isNotEmpty()) checked else hardware.wcdma
                } else {
                    setOf(5) // Workaround: enable just WCDMA Band V when WCDMA is deselected
                }
                val gsmBands = if (isGsmActive) s.gsmChecked.filterValues { it }.keys else emptySet()

                val state = SimState(
                    ratMask = s.ratChecked.filterValues { it }.keys,
                    gsmBands = gsmBands,
                    wcdmaBands = wcdmaBands,
                    lteBands = lteBands,
                    nrNsaBands = nrNsaBands,
                    nrSaBands = nrSaBands,
                    nrMode = if (isNrActive) s.nrMode else NrMode.DISABLE
                )

                fun rememberedBands(
                    family: BandFamily,
                    visible: Set<Int>,
                    remembered: Set<Int>?,
                    supported: Set<Int>
                ): Set<Int> = BandProfileResolver.resolveRememberedBands(
                    visibleBands = visible,
                    rememberedBands = remembered,
                    supportedBands = supported,
                    userEdited = s.editedBandFamilies[family] == true
                )

                val rememberedGsm = rememberedBands(
                    BandFamily.GSM, s.gsmChecked.filterValues { it }.keys,
                    desiredProfile?.gsmBands, hardware.gsm
                )
                val selectedWcdma = s.wcdmaChecked.filterValues { it }.keys.intersect(hardware.wcdma)
                val rememberedWcdma = if (isWcdmaActive) {
                    if (selectedWcdma.isNotEmpty()) selectedWcdma else rememberedBands(
                        BandFamily.WCDMA, selectedWcdma,
                        desiredProfile?.wcdmaBands, hardware.wcdma
                    )
                } else {
                    // Preserve remembered WCDMA selections (e.g. B1, B1+B8) when WCDMA is deselected
                    if (s.editedBandFamilies[BandFamily.WCDMA] == true && selectedWcdma.isNotEmpty()) {
                        selectedWcdma
                    } else if (desiredProfile?.wcdmaBands?.isNotEmpty() == true) {
                        desiredProfile.wcdmaBands
                    } else if (selectedWcdma.isNotEmpty()) {
                        selectedWcdma
                    } else {
                        setOf(1, 8)
                    }
                }
                val selectedLte = s.lteChecked.filterValues { it }.keys.intersect(displayHardware.lte)
                val rememberedLte = if (isLteActive) selectedLte else rememberedBands(
                    BandFamily.LTE, selectedLte,
                    desiredProfile?.lteBands?.intersect(displayHardware.lte), displayHardware.lte
                )
                val rememberedNsa: Set<Int>
                val rememberedSa: Set<Int>
                if (useIndependentLock) {
                    val nsaActiveForProfile = isNrActive &&
                        (s.nrMode == NrMode.NSA || s.nrMode == NrMode.BOTH)
                    val saActiveForProfile = isNrActive &&
                        (s.nrMode == NrMode.SA || s.nrMode == NrMode.BOTH)
                    rememberedNsa = if (nsaActiveForProfile) effectiveNrNsa else rememberedBands(
                        BandFamily.NR_NSA, selectedNrNsa,
                        desiredProfile?.nrNsaBands?.intersect(displayHardware.nr), displayHardware.nr
                    )
                    rememberedSa = if (saActiveForProfile) effectiveNrSa else rememberedBands(
                        BandFamily.NR_SA, selectedNrSa,
                        desiredProfile?.nrSaBands?.intersect(displayHardware.nr), displayHardware.nr
                    )
                } else {
                    val visibleNr = s.nrChecked.filterValues { it }.keys.intersect(displayHardware.nr)
                    val rememberedNr = desiredProfile?.nrSaBands?.takeIf { it.isNotEmpty() }
                        ?: desiredProfile?.nrNsaBands
                    val unified = if (isNrActive) visibleNr else rememberedBands(
                        BandFamily.NR, visibleNr, rememberedNr?.intersect(displayHardware.nr), displayHardware.nr
                    )
                    rememberedNsa = unified
                    rememberedSa = unified
                }

                val profile = SimState(
                    ratMask = s.ratChecked.filterValues { it }.keys,
                    gsmBands = rememberedGsm,
                    wcdmaBands = rememberedWcdma,
                    lteBands = rememberedLte,
                    nrNsaBands = rememberedNsa,
                    nrSaBands = rememberedSa,
                    nrMode = if (s.nrMode == NrMode.DISABLE)
                        s.lastEnabledNrMode else s.nrMode
                )
                onApply(selectedSim, state, profile)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
                    isRefreshing = refreshingSlots.contains(selectedSim),
                    onRefresh = { onRefresh(selectedSim) },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topBarHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = topBarHeight)
                            .padding(bottom = navbarSpace + applyResetSpace)
                    ) {
                        fun buildCurrentSimState(slotIndex: Int): SimState {
                            val s = slotStates[slotIndex]
                            val isNrActive = s.ratChecked[RatType.NR] == true
                            val isLteActive = s.ratChecked[RatType.LTE] == true
                            val isWcdmaActive = s.ratChecked[RatType.WCDMA] == true
                            val isGsmActive = s.ratChecked[RatType.GSM] == true
                            val nrBands = if (isNrActive) s.nrChecked.filterValues { it }.keys.intersect(displayHardware.nr) else emptySet()
                            val nrNsaBands = if (isNrActive) (if (useIndependentLock) s.nrNsaChecked.filterValues { it }.keys.intersect(displayHardware.nr) else nrBands) else emptySet()
                            val nrSaBands = if (isNrActive) (if (useIndependentLock) s.nrSaChecked.filterValues { it }.keys.intersect(displayHardware.nr) else nrBands) else emptySet()
                            val lteBands = if (isLteActive) s.lteChecked.filterValues { it }.keys.intersect(displayHardware.lte) else emptySet()
                            val wcdmaBands = if (isWcdmaActive) {
                                val checked = s.wcdmaChecked.filterValues { it }.keys.intersect(hardware.wcdma)
                                if (checked.isNotEmpty()) checked else hardware.wcdma
                            } else {
                                setOf(5) // Workaround: enable just WCDMA Band V when WCDMA is deselected
                            }
                            val gsmBands = if (isGsmActive) s.gsmChecked.filterValues { it }.keys else emptySet()

                            return SimState(
                                ratMask = s.ratChecked.filterValues { it }.keys,
                                gsmBands = gsmBands,
                                wcdmaBands = wcdmaBands,
                                lteBands = lteBands,
                                nrNsaBands = nrNsaBands,
                                nrSaBands = nrSaBands,
                                nrMode = if (isNrActive) {
                                    if (s.nrMode == NrMode.DISABLE || s.nrMode == NrMode.UNKNOWN)
                                        s.lastEnabledNrMode.enabledOrBoth() else s.nrMode
                                } else NrMode.DISABLE
                            )
                        }

                        // Single SIM view for Shannon (SIM 1)
                        val simState = modemState!!.sim1
                        val refreshKey = refreshKey0
                        SimBandLockPage(
                            state = slotStates[0],
                            simState = simState,
                            desiredProfile = desiredProfile,
                            hardware = hardware,
                            displayHardware = displayHardware,
                            useIndependentLock = useIndependentLock,
                            refreshKey = refreshKey,
                            onModeChange = { mode -> onModeChange(0, mode) }
                        )
                    }
                }

                if (backdrop != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        SmallTopAppBar(title = "Bands", color = Color.Transparent)
                    }
                } else {
                    SmallTopAppBar(title = "Bands")
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navbarSpace + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "Reset",
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReset(selectedSim)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                )
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        applyNow()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun SimBandLockPage(
    state: SlotBandState,
    simState: SimState?,
    desiredProfile: SimState?,
    hardware: HardwareBands,
    displayHardware: HardwareBands,
    useIndependentLock: Boolean,
    refreshKey: Int,
    onModeChange: (NrMode) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current

    fun restoreSelection(
        target: MutableMap<Int, Boolean>,
        remembered: Set<Int>?,
        supported: Set<Int>
    ) {
        val restored = BandProfileResolver.resolveRememberedBands(
            visibleBands = target.filterValues { it }.keys,
            rememberedBands = remembered,
            supportedBands = supported,
            userEdited = false
        )
        target.clear()
        restored.forEach { target[it] = true }
    }

    fun restoreRatBands(rat: RatType) {
        when (rat) {
            RatType.GSM -> restoreSelection(state.gsmChecked, desiredProfile?.gsmBands, hardware.gsm)
            RatType.WCDMA -> {
                val remembered = desiredProfile?.wcdmaBands?.takeIf { it.isNotEmpty() }
                    ?: state.wcdmaChecked.filterValues { it }.keys.takeIf { it.isNotEmpty() && it != setOf(5) }
                    ?: setOf(1, 8)
                restoreSelection(state.wcdmaChecked, remembered, hardware.wcdma)
            }
            RatType.LTE -> restoreSelection(state.lteChecked, desiredProfile?.lteBands, hardware.lte)
            RatType.NR -> {
                if (useIndependentLock) {
                    restoreSelection(state.nrSaChecked, desiredProfile?.nrSaBands, hardware.nr)
                    restoreSelection(state.nrNsaChecked, desiredProfile?.nrNsaBands, hardware.nr)
                } else {
                    val rememberedNr = desiredProfile?.nrSaBands?.takeIf { it.isNotEmpty() }
                        ?: desiredProfile?.nrNsaBands
                    restoreSelection(state.nrChecked, rememberedNr, hardware.nr)
                }
            }
        }
    }

    LaunchedEffect(simState, desiredProfile, refreshKey) {
        simState?.let { s ->
            val hasGsm = s.gsmBands.isNotEmpty()
            val hasWcdma = s.wcdmaBands.isNotEmpty()
            val hasLte = s.lteBands.isNotEmpty()
            val hasNr = (s.nrSaBands.isNotEmpty() || s.nrNsaBands.isNotEmpty()) && s.nrMode != NrMode.DISABLE
            val isAllEmpty = !hasGsm && !hasWcdma && !hasLte && !hasNr

            state.ratChecked.clear()
            val savedRatMask = desiredProfile?.ratMask
            if (savedRatMask != null && savedRatMask.isNotEmpty()) {
                BandConstants.ALL_RAT_TYPES.forEach { rt ->
                    state.ratChecked[rt] = savedRatMask.contains(rt)
                }
            } else if (s.ratMask.isNotEmpty()) {
                BandConstants.ALL_RAT_TYPES.forEach { rt ->
                    state.ratChecked[rt] = s.ratMask.contains(rt)
                }
            } else if (isAllEmpty) {
                BandConstants.ALL_RAT_TYPES.forEach { rt ->
                    state.ratChecked[rt] = true
                }
            } else {
                state.ratChecked[RatType.GSM] = hasGsm
                state.ratChecked[RatType.WCDMA] = hasWcdma
                state.ratChecked[RatType.LTE] = hasLte
                state.ratChecked[RatType.NR] = hasNr
            }

            // Visible checkboxes are a direct rendering of the latest modem query.
            // Remembered selections live separately in desiredProfile/DataStore.
            fun syncSelection(
                target: MutableMap<Int, Boolean>,
                queriedBands: Set<Int>
            ) {
                target.clear()
                queriedBands.forEach { target[it] = true }
            }

            syncSelection(state.gsmChecked, s.gsmBands)

            val isWcdmaActive = state.ratChecked[RatType.WCDMA] == true
            if (isWcdmaActive) {
                val wcdmaToDisplay = if (s.wcdmaBands.isNotEmpty() && s.wcdmaBands != setOf(5)) {
                    s.wcdmaBands
                } else if (desiredProfile?.wcdmaBands?.isNotEmpty() == true) {
                    desiredProfile.wcdmaBands
                } else if (s.wcdmaBands.isNotEmpty()) {
                    s.wcdmaBands
                } else {
                    setOf(1, 8)
                }
                syncSelection(state.wcdmaChecked, wcdmaToDisplay)
            } else {
                val rememberedWcdma = desiredProfile?.wcdmaBands?.takeIf { it.isNotEmpty() }
                    ?: (if (s.wcdmaBands.isNotEmpty() && s.wcdmaBands != setOf(5)) s.wcdmaBands else setOf(1, 8))
                syncSelection(state.wcdmaChecked, rememberedWcdma)
            }

            syncSelection(state.lteChecked, s.lteBands)

            val activeNrBands = when {
                s.nrSaBands.isNotEmpty() && s.nrNsaBands.isNotEmpty() -> {
                    if (s.nrNsaBands.size < hardware.nr.size && s.nrSaBands.size == hardware.nr.size) {
                        s.nrNsaBands
                    } else if (s.nrSaBands.size < hardware.nr.size && s.nrNsaBands.size == hardware.nr.size) {
                        s.nrSaBands
                    } else {
                        val common = s.nrSaBands.intersect(s.nrNsaBands)
                        if (common.isNotEmpty()) common else (s.nrSaBands + s.nrNsaBands)
                    }
                }
                s.nrNsaBands.isNotEmpty() -> s.nrNsaBands
                s.nrSaBands.isNotEmpty() -> s.nrSaBands
                else -> emptySet()
            }

            syncSelection(state.nrSaChecked, s.nrSaBands)
            syncSelection(state.nrNsaChecked, s.nrNsaBands)
            syncSelection(state.nrChecked, if (hasNr) activeNrBands else emptySet())
            state.editedBandFamilies.clear()

            when (s.nrMode) {
                NrMode.BOTH, NrMode.SA, NrMode.NSA -> {
                    state.nrMode = s.nrMode
                    state.lastEnabledNrMode = s.nrMode
                }
                // RAT-off is represented in the modem as NR mode disabled. Do not
                // erase the mode that should be restored when NR is enabled again.
                NrMode.DISABLE, NrMode.UNKNOWN -> {
                    val storedMode = desiredProfile?.nrMode
                    if (storedMode == NrMode.BOTH || storedMode == NrMode.SA || storedMode == NrMode.NSA) {
                        state.nrMode = storedMode
                        state.lastEnabledNrMode = storedMode
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle("RAT lock")
        val supportedRats = BandConstants.ALL_RAT_TYPES.filter { rt ->
            when (rt) {
                RatType.GSM -> hardware.gsm.isNotEmpty()
                RatType.WCDMA -> hardware.wcdma.isNotEmpty()
                RatType.LTE -> hardware.lte.isNotEmpty()
                RatType.NR -> hardware.nr.isNotEmpty()
            }
        }
        val isAuto = supportedRats.all { state.ratChecked[it] == true }
        val ratSummary = if (isAuto && supportedRats.isNotEmpty()) "AUTO (All RATs)"
            else if (state.ratChecked.values.all { it != true } || supportedRats.isEmpty()) "None"
            else supportedRats.filter { state.ratChecked[it] == true }.joinToString(", ") { it.name }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            WindowDropdownPreference(
                entries = listOf(
                    DropdownEntry(items = listOf(
                        DropdownItem(
                            text = "AUTO (All RATs)",
                            selected = isAuto,
                            onClick = {
                                val newAuto = !isAuto
                                supportedRats.forEach { state.ratChecked[it] = newAuto }
                                if (newAuto) supportedRats.forEach(::restoreRatBands)
                                if (newAuto && state.nrMode == NrMode.DISABLE) {
                                    state.nrMode = state.lastEnabledNrMode
                                }
                            }
                        )
                    )),
                    DropdownEntry(items = supportedRats.map { rt ->
                        DropdownItem(
                            text = rt.name,
                            selected = state.ratChecked[rt] == true,
                            onClick = {
                                val enabled = !(state.ratChecked[rt] == true)
                                state.ratChecked[rt] = enabled
                                if (enabled) restoreRatBands(rt)
                                if (rt == RatType.NR && enabled && state.nrMode == NrMode.DISABLE) {
                                    state.nrMode = state.lastEnabledNrMode
                                }
                                if (state.ratChecked[RatType.NR] == true &&
                                    (state.nrMode == NrMode.DISABLE || state.nrMode == NrMode.UNKNOWN)) {
                                    val restored = state.lastEnabledNrMode.enabledOrBoth()
                                    state.nrMode = restored
                                    state.lastEnabledNrMode = restored
                                }
                            }
                        )
                    })
                ),
                title = "RAT lock",
                summary = ratSummary,
                showValue = false,
                collapseOnSelection = false
            )
        }

        val isNrEnabled = state.ratChecked[RatType.NR] == true
        val isLteEnabled = state.ratChecked[RatType.LTE] == true
        val isWcdmaEnabled = state.ratChecked[RatType.WCDMA] == true
        val isGsmEnabled = state.ratChecked[RatType.GSM] == true
        // NR-only leaves no LTE anchor, so NSA cannot attach: grey it out.
        val isNrOnly = isNrEnabled && !isLteEnabled && !isWcdmaEnabled && !isGsmEnabled
        val isNrSaEnabled = isNrEnabled && (state.nrMode == NrMode.BOTH || state.nrMode == NrMode.SA)
        val isNrNsaEnabled = isNrEnabled && !isNrOnly &&
            (state.nrMode == NrMode.BOTH || state.nrMode == NrMode.NSA)

        if (hardware.nr.isNotEmpty()) {
            SmallTitle("NR mode", modifier = Modifier.then(if (isNrEnabled) Modifier else Modifier.alpha(0.4f)))
            val nrModeIndex = when (state.nrMode) {
                NrMode.BOTH, NrMode.UNKNOWN -> 0
                NrMode.SA -> 1
                NrMode.NSA -> 2
                // NR is disabled through the RAT selector. Keep this
                // selector on the last enabled mode while NR is off.
                NrMode.DISABLE -> when (state.lastEnabledNrMode) {
                    NrMode.SA -> 1
                    NrMode.NSA -> 2
                    else -> 0
                }
            }
            val nrModeEnabled = useIndependentLock && isNrEnabled
            TabRowWithContour(
                tabs = listOf("SA/NSA", "SA", "NSA"),
                selectedTabIndex = nrModeIndex,
                onTabSelected = { index ->
                    if (nrModeEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        val newMode = when (index) {
                            0 -> NrMode.BOTH
                            1 -> NrMode.SA
                            2 -> NrMode.NSA
                            else -> NrMode.BOTH
                        }
                        state.nrMode = newMode
                        state.lastEnabledNrMode = newMode
                        onModeChange(newMode)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (nrModeEnabled) Modifier else Modifier.alpha(0.4f))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SmallTitle("Band lock")
        Spacer(modifier = Modifier.height(4.dp))

        var recentlyClicked by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
        LaunchedEffect(recentlyClicked) {
            if (recentlyClicked != null) {
                delay(2000)
                recentlyClicked = null
            }
        }
        fun emojiFor(index: Int): String {
            val rc = recentlyClicked ?: return ""
            if (rc.first != index) return ""
            return if (rc.second) " ✓" else " ✗"
        }

        val hasNrHardware = hardware.nr.isNotEmpty()
        val quickItems = if (useIndependentLock) {
            if (hasNrHardware) {
                listOf(
                    "Clear 5G bands (NSA+SA)" to 1,
                    "Clear NR-SA bands" to 2,
                    "Clear NR-NSA bands" to 3,
                    "Clear LTE bands" to 4,
                    "Clear WCDMA bands" to 5,
                    "Clear GSM bands" to 6
                )
            } else {
                listOf(
                    "Clear LTE bands" to 4,
                    "Clear WCDMA bands" to 5,
                    "Clear GSM bands" to 6
                )
            }
        } else {
            if (hasNrHardware) {
                listOf(
                    "Clear NR bands" to 1,
                    "Clear LTE bands" to 4,
                    "Clear WCDMA bands" to 5,
                    "Clear GSM bands" to 6
                )
            } else {
                listOf(
                    "Clear LTE bands" to 4,
                    "Clear WCDMA bands" to 5,
                    "Clear GSM bands" to 6
                )
            }
        }

        val quickGroups = listOf(
            quickItems.filter { it.second in 1..3 },
            quickItems.filter { it.second in 4..6 }
        ).filter { it.isNotEmpty() }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            WindowDropdownPreference(
                entries = quickGroups.map { group ->
                    DropdownEntry(items = group.map { (label, idx) ->
                        DropdownItem(text = "$label${emojiFor(idx)}", onClick = {
                            when (idx) {
                                1 -> {
                                    if (useIndependentLock) {
                                        hardware.nr.forEach {
                                            state.nrNsaChecked[it] = false; state.nrSaChecked[it] = false
                                        }
                                        state.editedBandFamilies[BandFamily.NR_NSA] = true
                                        state.editedBandFamilies[BandFamily.NR_SA] = true
                                    } else {
                                        hardware.nr.forEach { state.nrChecked[it] = false }
                                        state.editedBandFamilies[BandFamily.NR] = true
                                    }
                                    recentlyClicked = idx to false
                                }
                                2 -> {
                                    hardware.nr.forEach { state.nrSaChecked[it] = false }
                                    state.editedBandFamilies[BandFamily.NR_SA] = true
                                    recentlyClicked = idx to false
                                }
                                3 -> {
                                    hardware.nr.forEach { state.nrNsaChecked[it] = false }
                                    state.editedBandFamilies[BandFamily.NR_NSA] = true
                                    recentlyClicked = idx to false
                                }
                                4 -> {
                                    hardware.lte.forEach { state.lteChecked[it] = false }
                                    state.editedBandFamilies[BandFamily.LTE] = true
                                    recentlyClicked = idx to false
                                }
                                5 -> {
                                    hardware.wcdma.forEach { state.wcdmaChecked[it] = false }
                                    state.editedBandFamilies[BandFamily.WCDMA] = true
                                    recentlyClicked = idx to false
                                }
                                6 -> {
                                    hardware.gsm.forEach { state.gsmChecked[it] = false }
                                    state.editedBandFamilies[BandFamily.GSM] = true
                                    recentlyClicked = idx to false
                                }
                            }
                        })
                    })
                },
                title = "Quick selections",
                summary = "Clear band groups",
                showValue = false,
                collapseOnSelection = false
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayHardware.nr.isNotEmpty()) {
            if (useIndependentLock) {
                SmallTitle("NR-SA", modifier = Modifier.then(if (isNrSaEnabled) Modifier else Modifier.alpha(0.4f)))
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        displayHardware.nr.sorted(), state.nrSaChecked, "n", enabled = isNrSaEnabled,
                        onSelectionChanged = { state.editedBandFamilies[BandFamily.NR_SA] = true }
                    )
                }
                SmallTitle("NR-NSA", modifier = Modifier.then(if (isNrNsaEnabled) Modifier else Modifier.alpha(0.4f)))
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        displayHardware.nr.sorted(), state.nrNsaChecked, "n", enabled = isNrNsaEnabled,
                        onSelectionChanged = { state.editedBandFamilies[BandFamily.NR_NSA] = true }
                    )
                }
            } else {
                SmallTitle("NR", modifier = Modifier.then(if (isNrEnabled) Modifier else Modifier.alpha(0.4f)))
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    BandCheckboxGrid(
                        displayHardware.nr.sorted(), state.nrChecked, "n", enabled = isNrEnabled,
                        onSelectionChanged = { state.editedBandFamilies[BandFamily.NR] = true }
                    )
                }
            }
        }
        if (displayHardware.lte.isNotEmpty()) {
            SmallTitle("LTE", modifier = Modifier.then(if (isLteEnabled) Modifier else Modifier.alpha(0.4f)))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                BandCheckboxGrid(
                    displayHardware.lte.sorted(), state.lteChecked, "B", enabled = isLteEnabled,
                    onSelectionChanged = { state.editedBandFamilies[BandFamily.LTE] = true }
                )
            }
        }
        if (hardware.wcdma.isNotEmpty()) {
            SmallTitle("WCDMA", modifier = Modifier.then(if (isWcdmaEnabled) Modifier else Modifier.alpha(0.4f)))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                BandCheckboxGrid(
                    hardware.wcdma.sorted(),
                    state.wcdmaChecked,
                    "B",
                    enabled = isWcdmaEnabled,
                    onSelectionChanged = { state.editedBandFamilies[BandFamily.WCDMA] = true }
                )
            }
        }
        if (hardware.gsm.isNotEmpty()) {
            SmallTitle("GSM", modifier = Modifier.then(if (isGsmEnabled) Modifier else Modifier.alpha(0.4f)))
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                BandCheckboxGrid(
                    hardware.gsm.sorted(), state.gsmChecked, "", enabled = isGsmEnabled,
                    onSelectionChanged = { state.editedBandFamilies[BandFamily.GSM] = true }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun BandCheckboxGrid(
    bands: List<Int>,
    checked: MutableMap<Int, Boolean>,
    prefix: String,
    enabled: Boolean = true,
    onSelectionChanged: () -> Unit = {}
) {
    val density = LocalDensity.current
    // Keep a little breathing room between rows without making the grid too tall.
    val rowMargin = with(density) { 5f.toDp() }
    val rowCount = (bands.size + 3) / 4
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.38f))
    ) {
        bands.chunked(4).forEachIndexed { index, group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (index == 0) 0.dp else rowMargin,
                        bottom = if (index == rowCount - 1) 0.dp else rowMargin
                    ),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                group.forEach { band ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isChecked = checked[band] == true
                        Checkbox(
                            state = if (isChecked) ToggleableState.On else ToggleableState.Off,
                            onClick = if (enabled) {
                                {
                                    checked[band] = checked[band] != true
                                    onSelectionChanged()
                                }
                            } else null
                        )
                        Text(
                            text = "$prefix$band",
                            style = MiuixTheme.textStyles.body1,
                            color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
                repeat(4 - group.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
