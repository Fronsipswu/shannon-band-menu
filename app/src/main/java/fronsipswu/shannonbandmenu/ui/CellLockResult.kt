package fronsipswu.shannonbandmenu.ui

/** Result model retained for the backend callbacks; cell locking is disabled on Shannon. */
data class CellLockResult(
    val sim: Int,
    val fieldIndex: Int,
    val type: String,
    val success: Boolean,
    val message: String?
)
