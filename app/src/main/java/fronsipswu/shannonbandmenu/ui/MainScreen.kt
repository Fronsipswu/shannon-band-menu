package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
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
    visibleGsmBands: Set<Int>? = null,
    visibleWcdmaBands: Set<Int>? = null,
    visibleLteBands: Set<Int>? = null,
    visibleNrSaBands: Set<Int>? = null,
    visibleNrNsaBands: Set<Int>? = null,
    onBandVisibilitySave: (Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?, Set<Int>?) -> Unit = { _, _, _, _, _ -> }
) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })
    val pagerScope = rememberCoroutineScope()

    fun selectPage(page: Int) {
        if (pagerState.currentPage == page && !pagerState.isScrollInProgress) return
        pagerScope.launch { pagerState.animateScrollToPage(page) }
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
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
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
                    selected = pagerState.currentPage == 1,
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
                    selected = pagerState.currentPage == 2,
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
                    selected = pagerState.currentPage == 3,
                    onClick = { selectPage(3) },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = "Info") },
                    label = { Text("Info") }
                )
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
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page == 0) {
                    NetworkInfoScreen(
                        isActive = pagerState.settledPage == 0 &&
                            !pagerState.isScrollInProgress,
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
            }

            if (isLoading && pagerState.settledPage != 0 &&
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
