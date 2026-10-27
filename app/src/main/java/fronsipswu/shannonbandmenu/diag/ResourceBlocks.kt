package fronsipswu.shannonbandmenu.diag

/** Configured channel maxima from TS 38.104 tables 5.3.2-1/2/3. */
private val NR_MAX_RB = mapOf(
    // FR1
    Triple(1, 15, 5) to 25, Triple(1, 15, 10) to 52, Triple(1, 15, 15) to 79, Triple(1, 15, 20) to 106,
    Triple(1, 15, 25) to 133, Triple(1, 15, 30) to 160, Triple(1, 15, 40) to 216, Triple(1, 15, 50) to 270,
    Triple(1, 30, 5) to 11, Triple(1, 30, 10) to 24, Triple(1, 30, 15) to 38, Triple(1, 30, 20) to 51,
    Triple(1, 30, 25) to 65, Triple(1, 30, 30) to 78, Triple(1, 30, 40) to 106, Triple(1, 30, 50) to 133,
    Triple(1, 30, 60) to 162, Triple(1, 30, 70) to 189, Triple(1, 30, 80) to 217, Triple(1, 30, 90) to 245,
    Triple(1, 30, 100) to 273,
    Triple(1, 60, 10) to 11, Triple(1, 60, 15) to 18, Triple(1, 60, 20) to 24, Triple(1, 60, 25) to 31,
    Triple(1, 60, 30) to 38, Triple(1, 60, 40) to 51, Triple(1, 60, 50) to 65, Triple(1, 60, 60) to 79,
    Triple(1, 60, 70) to 93, Triple(1, 60, 80) to 107, Triple(1, 60, 90) to 121, Triple(1, 60, 100) to 135,
    // FR2
    Triple(2, 60, 50) to 66, Triple(2, 60, 100) to 132, Triple(2, 60, 200) to 264,
    Triple(2, 120, 50) to 32, Triple(2, 120, 100) to 66, Triple(2, 120, 200) to 132, Triple(2, 120, 400) to 264
)


fun maxResourceBlocks(carrier: DiagCarrier): Double? {
    if (!carrier.nr) return 100.0
    val scs = carrier.scs?.toInt() ?: return null
    val bandwidth = carrier.bandwidth?.toInt() ?: return null
    val range = if ((carrier.band ?: 0) >= 257 || (carrier.arfcn ?: 0) >= 2_016_667) 2 else 1
    return NR_MAX_RB[Triple(range, scs, bandwidth)]?.toDouble()
}
