package fronsipswu.shannonbandmenu.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputScope

/** Defer double tap until a third tap has had a chance to arrive. */
internal suspend fun PointerInputScope.detectNetworkTaps(
    onDoubleTap: () -> Unit,
    onTripleTap: () -> Unit
) = awaitEachGesture {
    awaitFirstDown()
    val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        waitForUpOrCancellation()
    } ?: return@awaitEachGesture
    val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown()
    } ?: return@awaitEachGesture
    if (secondDown.uptimeMillis - firstUp.uptimeMillis < viewConfiguration.doubleTapMinTimeMillis)
        return@awaitEachGesture
    val secondUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        waitForUpOrCancellation()
    } ?: return@awaitEachGesture
    secondUp.consume()
    val thirdDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown()
    }
    if (thirdDown == null) {
        onDoubleTap()
    } else {
        if (thirdDown.uptimeMillis - secondUp.uptimeMillis < viewConfiguration.doubleTapMinTimeMillis)
            return@awaitEachGesture
        val thirdUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        } ?: return@awaitEachGesture
        thirdUp.consume()
        onTripleTap()
    }
}
