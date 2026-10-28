package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fronsipswu.shannonbandmenu.ModemState
import fronsipswu.shannonbandmenu.FrequencyLockState
import fronsipswu.shannonbandmenu.NrMode
import fronsipswu.shannonbandmenu.R
import fronsipswu.shannonbandmenu.SimState
import kotlinx.coroutines.launch

@Composable
@Suppress("UNUSED_PARAMETER")
fun MainScreen(
    onApply: (Int, SimState, SimState) -> Unit,
    onReset: (Int) -> Unit,
    onModeChange: (Int, NrMode) -> Unit = { _, _ -> },
    refreshingSlots: Set<Int>,
    onRefresh: (Int) -> Unit,
    refreshKey0: Int,
    refreshKey1: Int,
    modemState: ModemState?,
    desiredProfile: SimState?,
    isLoading: Boolean,
    showRootDeniedDialog: Boolean,
    onRootRetry: () -> Unit,
    onDismissRootDialog: () -> Unit,
    showErrorDialog: Boolean,
    errorDialogTitle: String,
    errorDialogMessage: String,
    onDismissErrorDialog: () -> Unit,
    snackbarHostState: SnackbarHostState,
    snackbarMessage: String?,
    snackbarIsError: Boolean,
    onSnackbarShown: () -> Unit,
    debugEnabled: Boolean,
    onDebugToggle: () -> Unit,
    frequencyLockState: FrequencyLockState,
    frequencyLockRefreshing: Boolean,
    frequencyLockRefreshKey: Int,
    onFrequencyLockRefresh: () -> Unit,
    onFrequencyLockApply: (List<Int>, Int?, Int?, Int?) -> Unit,
    onFrequencyLockReset: () -> Unit,
    onCellLockSimSwitch: (Int) -> Unit,
    onCellLockApply: (Int, Int, String) -> Unit,
    onCellLockClearAll: (Int) -> Unit,
    onCellLockClear5G: (Int) -> Unit,
    onCellLockClear4G: (Int) -> Unit,
    onCellLockClearPlmn: (Int) -> Unit,
    cellLockResult: CellLockResult?,
    onCellLockResultConsumed: () -> Unit,
    cellLockRefreshing: Boolean,
    onCellLockRefresh: () -> Unit,
    cellLockRefreshKey: Int,
    nrIndependentSupported: Boolean?,
    quickSelectHidden: Boolean = false,
    onToggleQuickSelect: () -> Unit = {},
    nrModeHidden: Boolean = false,
    navbarHidden: Boolean = false,
    onNavbarHiddenToggle: (Boolean) -> Unit = {},
    onToggleNrMode: () -> Unit = {},
    visibleGsmBands: Set<Int>? = null,
    visibleWcdmaBands: Set<Int>? = null,
    visibleLteBands: Set<Int>? = null,
    visibleNrSaBands: Set<Int>? = null,
    visibleNrNsaBands: Set<Int>? = null,
    onBandVisibilitySave: (Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?) -> Unit = { _, _, _, _, _ -> }
) {
    ForegroundNetworkMonitoring()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp
        val paneGap = if (twoPane) 12.dp else 0.dp
        val pageSize = if (twoPane) PageSize.Fixed((maxWidth - paneGap) / 2) else PageSize.Fill
        val pagerState = rememberPagerState(initialPage = if (twoPane) 0 else 1, pageCount = { 4 })
        val pagerScope = rememberCoroutineScope()
        val visiblePages = pagerState.currentPage..(pagerState.currentPage + if (twoPane) 1 else 0)

        LaunchedEffect(twoPane) {
            if (twoPane) pagerState.scrollToPage(pagerState.currentPage / 2 * 2)
        }

        fun selectPage(page: Int) {
            // The last page shares a spread with its predecessor, rather than
            // leaving half of the unfolded display empty.
            val target = if (twoPane) page.coerceAtMost(2) else page
            if (pagerState.currentPage == target && !pagerState.isScrollInProgress) return
            pagerScope.launch { pagerState.animateScrollToPage(target) }
        }
        LaunchedEffect(snackbarMessage) {
            snackbarMessage?.let {
                snackbarHostState.showSnackbar(it)
                onSnackbarShown()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!navbarHidden) NavigationBar {
                    NavigationBarItem(
                        selected = 0 in visiblePages,
                        onClick = { selectPage(0) },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_network_info),
                                contentDescription = "Telephony Info"
                            )
                        },
                        label = { Text("Telephony") }
                    )
                    NavigationBarItem(
                        selected = 1 in visiblePages,
                        onClick = { selectPage(1) },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_tune),
                                contentDescription = "Bands"
                            )
                        },
                        label = { Text("Bands") }
                    )
                    NavigationBarItem(
                        selected = 2 in visiblePages,
                        onClick = { selectPage(2) },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_cell_lock),
                                contentDescription = "Cell Lock"
                            )
                        },
                        label = { Text("Cell Lock") }
                    )
                    NavigationBarItem(
                        selected = 3 in visiblePages,
                        onClick = { selectPage(3) },
                        icon = { Icon(Icons.Outlined.Info, contentDescription = "Info") },
                        label = { Text("Info") }
                    )
                } else {
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            },
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (snackbarIsError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.inverseSurface
                        },
                        contentColor = if (snackbarIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.inverseOnSurface
                        }
                    )
                }
            }
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    pageSize = pageSize,
                    pageSpacing = paneGap,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        if (page == 0) {
                            NetworkInfoScreen(
                                // Selection only controls replay playback, never collection.
                                isActive = pagerState.settledPage == 0,
                                contentPadding = contentPadding
                            )
                        } else if (page == 1) {
                            BandLockScreen(
                                modemState = modemState,
                                desiredProfile = desiredProfile,
                                isLoading = isLoading,
                                refreshingSlots = refreshingSlots,
                                onRefresh = onRefresh,
                                refreshKey0 = refreshKey0,
                                refreshKey1 = refreshKey1,
                                onApply = onApply,
                                onReset = onReset,
                                onModeChange = onModeChange,
                                debugEnabled = debugEnabled,
                                onDebugToggle = onDebugToggle,
                                nrIndependentSupported = nrIndependentSupported,
                                quickSelectHidden = quickSelectHidden,
                                onToggleQuickSelect = onToggleQuickSelect,
                                nrModeHidden = nrModeHidden,
                                onToggleNrMode = onToggleNrMode,
                                visibleGsmBands = visibleGsmBands,
                                visibleWcdmaBands = visibleWcdmaBands,
                                visibleLteBands = visibleLteBands,
                                visibleNrSaBands = visibleNrSaBands,
                                visibleNrNsaBands = visibleNrNsaBands,
                                contentPadding = contentPadding,
                                onBandVisibilitySave = onBandVisibilitySave
                            )
                        } else if (page == 2) {
                            FrequencyLockScreen(
                                state = frequencyLockState,
                                isRefreshing = frequencyLockRefreshing,
                                refreshKey = frequencyLockRefreshKey,
                                onRefresh = onFrequencyLockRefresh,
                                onApply = onFrequencyLockApply,
                                onReset = onFrequencyLockReset,
                                contentPadding = contentPadding
                            )
                        } else {
                            InfoScreen(
                                contentPadding = contentPadding,
                                navbarHidden = navbarHidden,
                                onNavbarHiddenToggle = onNavbarHiddenToggle,
                                debugEnabled = debugEnabled,
                                onDebugToggle = onDebugToggle,
                                hardware = modemState?.hardware,
                                visibleGsmBands = visibleGsmBands,
                                visibleWcdmaBands = visibleWcdmaBands,
                                visibleLteBands = visibleLteBands,
                                visibleNrSaBands = visibleNrSaBands,
                                visibleNrNsaBands = visibleNrNsaBands,
                                onBandVisibilitySave = onBandVisibilitySave
                            )
                        }
                        if (isLoading && page != 0 &&
                            !pagerState.isScrollInProgress
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRootDeniedDialog) {
            AlertDialog(
                onDismissRequest = onDismissRootDialog,
                title = { Text("Root access required") },
                text = { Text("Grant root access so Shannon Band Menu can communicate with the modem.") },
                confirmButton = { TextButton(onClick = onRootRetry) { Text("Retry") } },
                dismissButton = { TextButton(onClick = onDismissRootDialog) { Text("Close") } }
            )
        }

        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = onDismissErrorDialog,
                title = { Text(errorDialogTitle) },
                text = { Text(errorDialogMessage) },
                confirmButton = { TextButton(onClick = onDismissErrorDialog) { Text("OK") } }
            )
        }
    }
}
