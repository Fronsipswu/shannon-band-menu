package fronsipswu.shannonbandmenu

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

enum class NetworkCellRole {
    PRIMARY,
    SECONDARY
}

data class NetworkCell(
    val technology: String,
    val role: NetworkCellRole,
    val simSlot: Int,
    val registered: Boolean,
    val physicalId: Int? = null,
    val cellId: Long? = null,
    val channel: Int? = null,
    val band: Int? = null,
    val bandwidthKhz: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    /** LTE RSSNR and NR SS-SINR are already reported in dB. */
    val sinr: Int? = null,
    val cqi: Int? = null,
    val timingAdvance: Int? = null,
    val nrCsiRsrp: Int? = null,
    val nrCsiRsrq: Int? = null,
    val nrCsiSinr: Int? = null,
    val ageMillis: Long? = null
)

data class NetworkSubscription(
    val subscriptionId: Int,
    val simSlot: Int,
    val operatorName: String,
    val mcc: String?,
    val mnc: String?,
    val countryIso: String,
    val roaming: Boolean,
    val voiceNetwork: String,
    val dataNetwork: String,
    val dataState: String,
    val bandwidthsKhz: List<Int>,
    val cells: List<NetworkCell>
)

data class NetworkInfoSnapshot(
    val subscriptions: List<NetworkSubscription> = emptyList(),
    val locationEnabled: Boolean = false,
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val error: String? = null
)

data class RadioFrequencies(
    val downlinkMhz: Double,
    val uplinkMhz: Double?
)

private data class LteBandPlan(
    val band: Int,
    val firstEarfcn: Int,
    val lastEarfcn: Int,
    val downlinkLowMhz: Double,
    val uplinkLowMhz: Double?
)

private val LTE_BAND_PLANS = listOf(
    LteBandPlan(1, 0, 599, 2110.0, 1920.0),
    LteBandPlan(2, 600, 1199, 1930.0, 1850.0),
    LteBandPlan(3, 1200, 1949, 1805.0, 1710.0),
    LteBandPlan(4, 1950, 2399, 2110.0, 1710.0),
    LteBandPlan(5, 2400, 2649, 869.0, 824.0),
    LteBandPlan(6, 2650, 2749, 875.0, 830.0),
    LteBandPlan(7, 2750, 3449, 2620.0, 2500.0),
    LteBandPlan(8, 3450, 3799, 925.0, 880.0),
    LteBandPlan(9, 3800, 4149, 1844.9, 1749.9),
    LteBandPlan(10, 4150, 4749, 2110.0, 1710.0),
    LteBandPlan(11, 4750, 4949, 1475.9, 1427.9),
    LteBandPlan(12, 5010, 5179, 729.0, 699.0),
    LteBandPlan(13, 5180, 5279, 746.0, 777.0),
    LteBandPlan(14, 5280, 5379, 758.0, 788.0),
    LteBandPlan(17, 5730, 5849, 734.0, 704.0),
    LteBandPlan(18, 5850, 5999, 860.0, 815.0),
    LteBandPlan(19, 6000, 6149, 875.0, 830.0),
    LteBandPlan(20, 6150, 6449, 791.0, 832.0),
    LteBandPlan(21, 6450, 6599, 1495.9, 1447.9),
    LteBandPlan(22, 6600, 7399, 3510.0, 3410.0),
    LteBandPlan(23, 7500, 7699, 2180.0, 2000.0),
    LteBandPlan(24, 7700, 8039, 1525.0, 1626.5),
    LteBandPlan(25, 8040, 8689, 1930.0, 1850.0),
    LteBandPlan(26, 8690, 9039, 859.0, 814.0),
    LteBandPlan(27, 9040, 9209, 852.0, 807.0),
    LteBandPlan(28, 9210, 9659, 758.0, 703.0),
    LteBandPlan(29, 9660, 9769, 717.0, null),
    LteBandPlan(30, 9770, 9869, 2350.0, 2305.0),
    LteBandPlan(31, 9870, 9919, 462.5, 452.5),
    LteBandPlan(32, 9920, 10359, 1452.0, null),
    LteBandPlan(33, 36000, 36199, 1900.0, 1900.0),
    LteBandPlan(34, 36200, 36349, 2010.0, 2010.0),
    LteBandPlan(35, 36350, 36949, 1850.0, 1850.0),
    LteBandPlan(36, 36950, 37549, 1930.0, 1930.0),
    LteBandPlan(37, 37550, 37749, 1910.0, 1910.0),
    LteBandPlan(38, 37750, 38249, 2570.0, 2570.0),
    LteBandPlan(39, 38250, 38649, 1880.0, 1880.0),
    LteBandPlan(40, 38650, 39649, 2300.0, 2300.0),
    LteBandPlan(41, 39650, 41589, 2496.0, 2496.0),
    LteBandPlan(42, 41590, 43589, 3400.0, 3400.0),
    LteBandPlan(43, 43590, 45589, 3600.0, 3600.0),
    LteBandPlan(44, 45590, 46589, 703.0, 703.0),
    LteBandPlan(46, 46790, 54539, 5150.0, 5150.0),
    LteBandPlan(48, 55240, 56739, 3550.0, 3550.0),
    LteBandPlan(65, 65536, 66435, 2110.0, 1920.0),
    LteBandPlan(66, 66436, 67335, 2110.0, 1710.0),
    LteBandPlan(67, 67336, 67535, 738.0, null),
    LteBandPlan(68, 67536, 67835, 753.0, 698.0),
    LteBandPlan(69, 67836, 68335, 2570.0, null),
    LteBandPlan(70, 68336, 68585, 1995.0, 1695.0),
    LteBandPlan(71, 68586, 68935, 617.0, 663.0),
    LteBandPlan(72, 68936, 68985, 461.0, 451.0),
    LteBandPlan(73, 68986, 69035, 460.0, 450.0),
    LteBandPlan(74, 69036, 69465, 1475.0, 1427.0),
    LteBandPlan(75, 69466, 70315, 1432.0, null),
    LteBandPlan(76, 70316, 70365, 1427.0, null),
    LteBandPlan(85, 70366, 70545, 728.0, 698.0),
    LteBandPlan(87, 70546, 70595, 420.0, 410.0),
    LteBandPlan(88, 70596, 70645, 422.0, 412.0)
)

fun lteBandForEarfcn(earfcn: Int): Int? =
    LTE_BAND_PLANS.firstOrNull { earfcn in it.firstEarfcn..it.lastEarfcn }?.band

fun lteFrequenciesForEarfcn(earfcn: Int): RadioFrequencies? {
    val plan = LTE_BAND_PLANS.firstOrNull { earfcn in it.firstEarfcn..it.lastEarfcn }
        ?: return null
    val offsetMhz = (earfcn - plan.firstEarfcn) / 10.0
    return RadioFrequencies(
        downlinkMhz = plan.downlinkLowMhz + offsetMhz,
        uplinkMhz = plan.uplinkLowMhz?.plus(offsetMhz)
    )
}

fun nrFrequencyForArfcn(arfcn: Int): Double? = when (arfcn) {
    in 0..599_999 -> arfcn * 0.005
    in 600_000..2_016_666 -> 3000.0 + (arfcn - 600_000) * 0.015
    in 2_016_667..3_279_165 -> 24_250.08 + (arfcn - 2_016_667) * 0.06
    else -> null
}

fun lteTimingAdvanceMeters(timingAdvance: Int?): Double? =
    timingAdvance?.takeIf { it >= 0 }?.times(78.125)

fun carrierAggregationLabel(
    cells: List<NetworkCell>,
    bandwidthsKhz: List<Int>
): String {
    val primary = cells.filter { it.role == NetworkCellRole.PRIMARY }
    val secondary = cells.filter { it.role == NetworkCellRole.SECONDARY }
    val servingCells = primary + secondary

    val fallback = bandwidthsKhz.filter { it > 0 && it != CellInfo.UNAVAILABLE }
    val carriers = max(servingCells.size, fallback.size)
    if (carriers <= 1) return "No"

    val bandList = servingCells.mapNotNull { cell ->
        cell.band?.let { band ->
            val prefix = if (cell.technology == "NR") "n" else "B"
            "$prefix$band"
        }
    }

    return if (bandList.isNotEmpty()) {
        "${carriers}CA (${bandList.joinToString("+")})"
    } else {
        "${carriers}CA"
    }
}

fun totalBandwidthLabel(
    cells: List<NetworkCell>,
    bandwidthsKhz: List<Int>
): String? {
    val primary = cells.filter { it.role == NetworkCellRole.PRIMARY }
    val secondary = cells.filter { it.role == NetworkCellRole.SECONDARY }
    val servingCells = primary + secondary

    val fallback = bandwidthsKhz.filter { it > 0 && it != CellInfo.UNAVAILABLE }
    val widths = if (servingCells.isNotEmpty()) {
        val mapped = servingCells.mapIndexedNotNull { index, cell ->
            cell.bandwidthKhz?.takeIf { it > 0 && it != CellInfo.UNAVAILABLE }
                ?: fallback.getOrNull(index)
        }
        mapped.ifEmpty { fallback }
    } else {
        fallback
    }

    if (widths.isEmpty()) return null

    val totalKhz = widths.sum()
    val totalMhz = totalKhz / 1000.0
    val totalMhzStr = if (totalMhz % 1.0 == 0.0) "${totalMhz.toInt()} MHz" else "$totalMhz MHz"

    if (widths.size <= 1) {
        return totalMhzStr
    }

    val components = widths.joinToString("+") { khz ->
        val m = khz / 1000.0
        if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()
    }

    return "$totalMhzStr ($components)"
}

fun lteBandwidthsForNsa(
    cells: List<NetworkCell>,
    bandwidthsKhz: List<Int>
): List<Int> {
    val lteServingCount = cells.count {
        it.technology == "LTE" &&
            (it.role == NetworkCellRole.PRIMARY || it.role == NetworkCellRole.SECONDARY)
    }
    val valid = bandwidthsKhz.filter { it > 0 && it != CellInfo.UNAVAILABLE }
    return if (lteServingCount > 0) valid.take(lteServingCount) else valid
}

fun nrSaCarrierAggregationLabel(
    cells: List<NetworkCell>,
    bandwidthsKhz: List<Int>
): String {
    val nrServing = cells.count {
        it.technology == "NR" &&
            (it.role == NetworkCellRole.PRIMARY || it.role == NetworkCellRole.SECONDARY)
    }
    val bandwidths = bandwidthsKhz.filter { it > 0 && it != CellInfo.UNAVAILABLE }
    val carriers = max(nrServing, bandwidths.size)
    if (carriers <= 1) return "No"
    val widths = bandwidths.joinToString("+") { bandwidth ->
        val mhz = bandwidth / 1000.0
        if (mhz % 1.0 == 0.0) mhz.toInt().toString() else mhz.toString()
    }
    return if (widths.isEmpty()) "${carriers}CA" else "${carriers}CA ($widths MHz)"
}

fun isNrNsa(cells: List<NetworkCell>): Boolean =
    cells.any { it.role == NetworkCellRole.PRIMARY && it.technology == "LTE" } &&
        cells.any { it.role == NetworkCellRole.SECONDARY && it.technology == "NR" }

fun bandwidthLabelForTechnology(
    technology: String,
    cells: List<NetworkCell>,
    bandwidthsKhz: List<Int>
): String {
    val servingCells = cells.filter {
        (it.role == NetworkCellRole.PRIMARY || it.role == NetworkCellRole.SECONDARY) &&
            it.technology == technology
    }
    val fallback = bandwidthsKhz.filter { it > 0 && it != CellInfo.UNAVAILABLE }
    val lteServingCount = cells.count {
        (it.role == NetworkCellRole.PRIMARY || it.role == NetworkCellRole.SECONDARY) &&
            it.technology == "LTE"
    }
    val fallbackForTechnology = if (technology == "NR") {
        fallback.drop(lteServingCount)
    } else {
        fallback
    }
    val widths = servingCells.mapIndexedNotNull { index, cell ->
        cell.bandwidthKhz?.takeIf { it > 0 && it != CellInfo.UNAVAILABLE }
            ?: fallbackForTechnology.getOrNull(index)
    }
    val carriers = max(servingCells.size, widths.size)
    val formattedWidths = widths.joinToString("+") { bandwidth ->
        val mhz = bandwidth / 1000.0
        if (mhz % 1.0 == 0.0) mhz.toInt().toString() else mhz.toString()
    }
    if (technology == "NR") {
        return if (formattedWidths.isEmpty()) "Unknown" else "$formattedWidths MHz"
    }
    return if (carriers <= 1) {
        if (formattedWidths.isEmpty()) "Non-CA" else "Non-CA ($formattedWidths MHz)"
    } else {
        if (formattedWidths.isEmpty()) "${carriers}CA"
        else "${carriers}CA ($formattedWidths MHz)"
    }
}

class NetworkInfoSource(private val context: Context) {
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)

    fun hasPhonePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasFineLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun readSnapshot(): NetworkInfoSnapshot {
        if (!hasPhonePermission()) {
            return NetworkInfoSnapshot(
                locationEnabled = locationEnabled(),
                error = "Phone permission is required."
            )
        }

        return try {
            val subscriptions = activeSubscriptions().map { subscription ->
                val manager = telephonyManager.createForSubscriptionId(subscription.subscriptionId)
                val cells = if (hasFineLocationPermission()) {
                    runCatching { readCells(manager, subscription.simSlotIndex) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val service = runCatching { manager.serviceState }.getOrNull()
                NetworkSubscription(
                    subscriptionId = subscription.subscriptionId,
                    simSlot = subscription.simSlotIndex,
                    operatorName = manager.networkOperatorName.ifBlank {
                        subscription.carrierName?.toString().orEmpty()
                    },
                    mcc = subscription.mccString?.takeIf(String::isNotBlank)
                        ?: splitOperator(manager.networkOperator).first,
                    mnc = subscription.mncString?.takeIf(String::isNotBlank)
                        ?: splitOperator(manager.networkOperator).second,
                    countryIso = manager.networkCountryIso,
                    roaming = manager.isNetworkRoaming,
                    voiceNetwork = networkTypeName(manager.voiceNetworkType).let { voiceNetwork ->
                        if (voiceNetwork == "NR") "NR-SA" else voiceNetwork
                    },
                    dataNetwork = networkTypeName(manager.dataNetworkType).let { dataNetwork ->
                        when {
                            dataNetwork == "LTE" && isNrNsa(cells) -> "NR-NSA"
                            dataNetwork == "NR" -> "NR-SA"
                            else -> dataNetwork
                        }
                    },
                    dataState = dataStateName(manager.dataState),
                    bandwidthsKhz = service?.cellBandwidths
                        ?.filter { it > 0 && it != CellInfo.UNAVAILABLE }
                        .orEmpty(),
                    cells = cells
                )
            }
            NetworkInfoSnapshot(
                subscriptions = subscriptions,
                locationEnabled = locationEnabled()
            )
        } catch (e: SecurityException) {
            NetworkInfoSnapshot(
                locationEnabled = locationEnabled(),
                error = "Android denied telephony access: ${e.message}"
            )
        } catch (e: RuntimeException) {
            NetworkInfoSnapshot(
                locationEnabled = locationEnabled(),
                error = "Unable to read telephony state: ${e.message}"
            )
        }
    }

    suspend fun requestFreshCellInfo() {
        if (!hasPhonePermission() || !hasFineLocationPermission()) return
        val managers = activeSubscriptions().map {
            telephonyManager.createForSubscriptionId(it.subscriptionId)
        }
        managers.forEach { manager ->
            withTimeoutOrNull(4_000) {
                suspendCancellableCoroutine { continuation ->
                    val completed = AtomicBoolean(false)
                    fun finish() {
                        if (completed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                    try {
                        manager.requestCellInfoUpdate(
                            context.mainExecutor,
                            object : TelephonyManager.CellInfoCallback() {
                                override fun onCellInfo(cellInfo: List<CellInfo>) = finish()

                                override fun onError(errorCode: Int, detail: Throwable?) = finish()
                            }
                        )
                    } catch (_: RuntimeException) {
                        finish()
                    }
                }
            }
        }
    }

    private fun activeSubscriptions(): List<SubscriptionInfo> =
        subscriptionManager.activeSubscriptionInfoList.orEmpty()

    private fun locationEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java).isLocationEnabled

    private fun readCells(manager: TelephonyManager, simSlot: Int): List<NetworkCell> =
        manager.allCellInfo.orEmpty()
            .filter { cell ->
                cell.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING ||
                    cell.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING
            }
            .mapNotNull { cell -> mapCell(cell, simSlot) }

    private fun mapCell(cell: CellInfo, simSlot: Int): NetworkCell? {
        val role = if (cell.cellConnectionStatus == CellInfo.CONNECTION_PRIMARY_SERVING) {
            NetworkCellRole.PRIMARY
        } else {
            NetworkCellRole.SECONDARY
        }
        val ageMillis = availableAge(cell.timestampMillis)

        return when (cell) {
            is CellInfoLte -> mapLteCell(cell, role, simSlot, ageMillis)
            is CellInfoNr -> mapNrCell(cell, role, simSlot, ageMillis)
            is CellInfoWcdma -> mapWcdmaCell(cell, role, simSlot, ageMillis)
            is CellInfoGsm -> mapGsmCell(cell, role, simSlot, ageMillis)
            is CellInfoTdscdma -> mapTdscdmaCell(cell, role, simSlot, ageMillis)
            else -> null
        }
    }

    private fun mapLteCell(
        cell: CellInfoLte,
        role: NetworkCellRole,
        simSlot: Int,
        ageMillis: Long?
    ): NetworkCell {
        val identity: CellIdentityLte = cell.cellIdentity
        val signal: CellSignalStrengthLte = cell.cellSignalStrength
        val earfcn = available(identity.earfcn)
        return NetworkCell(
            technology = "LTE",
            role = role,
            simSlot = simSlot,
            registered = cell.isRegistered,
            physicalId = available(identity.pci),
            cellId = available(identity.ci)?.toLong(),
            channel = earfcn,
            band = earfcn?.let(::lteBandForEarfcn) ?: identity.bands.firstOrNull(),
            bandwidthKhz = available(identity.bandwidth),
            rssi = available(signal.rssi),
            rsrp = available(signal.rsrp),
            rsrq = available(signal.rsrq),
            // Android reports LTE RSSNR directly in dB on this modem.
            sinr = available(signal.rssnr),
            cqi = available(signal.cqi),
            timingAdvance = available(signal.timingAdvance),
            ageMillis = ageMillis
        )
    }

    private fun mapNrCell(
        cell: CellInfoNr,
        role: NetworkCellRole,
        simSlot: Int,
        ageMillis: Long?
    ): NetworkCell {
        val identity = cell.cellIdentity as CellIdentityNr
        val signal = cell.cellSignalStrength as CellSignalStrengthNr
        return NetworkCell(
            technology = "NR",
            role = role,
            simSlot = simSlot,
            registered = cell.isRegistered,
            physicalId = available(identity.pci),
            cellId = available(identity.nci),
            channel = available(identity.nrarfcn),
            band = identity.bands.firstOrNull(),
            rsrp = available(signal.ssRsrp),
            rsrq = available(signal.ssRsrq),
            // This modem exposes unavailable NR SINR as 0 through the public getter.
            sinr = availableNrMetric(signal.ssSinr),
            timingAdvance = if (Build.VERSION.SDK_INT >= 34) {
                available(signal.timingAdvanceMicros)
            } else {
                null
            },
            nrCsiRsrp = available(signal.csiRsrp),
            nrCsiRsrq = available(signal.csiRsrq),
            nrCsiSinr = availableNrMetric(signal.csiSinr),
            ageMillis = ageMillis
        )
    }

    private fun mapWcdmaCell(
        cell: CellInfoWcdma,
        role: NetworkCellRole,
        simSlot: Int,
        ageMillis: Long?
    ): NetworkCell {
        val identity: CellIdentityWcdma = cell.cellIdentity
        val signal: CellSignalStrengthWcdma = cell.cellSignalStrength
        return NetworkCell(
            technology = "WCDMA",
            role = role,
            simSlot = simSlot,
            registered = cell.isRegistered,
            physicalId = available(identity.psc),
            cellId = available(identity.cid)?.toLong(),
            channel = available(identity.uarfcn),
            rsrp = available(signal.dbm),
            sinr = available(signal.ecNo),
            ageMillis = ageMillis
        )
    }

    private fun mapGsmCell(
        cell: CellInfoGsm,
        role: NetworkCellRole,
        simSlot: Int,
        ageMillis: Long?
    ): NetworkCell {
        val identity: CellIdentityGsm = cell.cellIdentity
        val signal: CellSignalStrengthGsm = cell.cellSignalStrength
        return NetworkCell(
            technology = "GSM",
            role = role,
            simSlot = simSlot,
            registered = cell.isRegistered,
            physicalId = available(identity.bsic),
            cellId = available(identity.cid)?.toLong(),
            channel = available(identity.arfcn),
            rssi = available(signal.rssi),
            timingAdvance = available(signal.timingAdvance),
            ageMillis = ageMillis
        )
    }

    private fun mapTdscdmaCell(
        cell: CellInfoTdscdma,
        role: NetworkCellRole,
        simSlot: Int,
        ageMillis: Long?
    ): NetworkCell {
        val identity: CellIdentityTdscdma = cell.cellIdentity
        val signal: CellSignalStrengthTdscdma = cell.cellSignalStrength
        return NetworkCell(
            technology = "TD-SCDMA",
            role = role,
            simSlot = simSlot,
            registered = cell.isRegistered,
            physicalId = available(identity.cpid),
            cellId = available(identity.cid)?.toLong(),
            channel = available(identity.uarfcn),
            rsrp = available(signal.rscp),
            ageMillis = ageMillis
        )
    }

    private fun available(value: Int): Int? =
        value.takeUnless { it == CellInfo.UNAVAILABLE || it == Int.MAX_VALUE }

    private fun available(value: Long): Long? =
        value.takeUnless { it == Long.MAX_VALUE || it == CellInfo.UNAVAILABLE.toLong() }

    private fun availableNrMetric(value: Int): Int? =
        value.takeUnless { it == 0 || it == Int.MAX_VALUE || it == CellInfo.UNAVAILABLE }

    private fun availableAge(timestampMillis: Long): Long? =
        timestampMillis.takeIf { it > 0 }?.let { max(0, SystemClock.elapsedRealtime() - it) }

    private fun splitOperator(operator: String): Pair<String?, String?> =
        if (operator.length >= 5) operator.take(3) to operator.drop(3) else null to null

    private fun networkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO 0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO A"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO B"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        else -> "Unknown"
    }

    private fun dataStateName(state: Int): String = when (state) {
        TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
        TelephonyManager.DATA_CONNECTING -> "Connecting"
        TelephonyManager.DATA_CONNECTED -> "Connected"
        TelephonyManager.DATA_SUSPENDED -> "Suspended"
        TelephonyManager.DATA_DISCONNECTING -> "Disconnecting"
        TelephonyManager.DATA_HANDOVER_IN_PROGRESS -> "Handover"
        else -> "Unknown"
    }

}
