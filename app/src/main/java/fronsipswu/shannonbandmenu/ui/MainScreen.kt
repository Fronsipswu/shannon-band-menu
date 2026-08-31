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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import fronsipswu.shannonbandmenu.ModemState
import fronsipswu.shannonbandmenu.NrMode
import fronsipswu.shannonbandmenu.R
import fronsipswu.shannonbandmenu.SimState

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
    var selectedIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(selectedIndex) {
        if (pagerState.targetPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState.targetPage) {
        selectedIndex = pagerState.targetPage
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
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_tune),
                            contentDescription = "Bands"
                        )
                    },
                    label = { Text("Bands") }
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
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

            if (isLoading) {
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
