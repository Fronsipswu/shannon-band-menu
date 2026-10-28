package fronsipswu.shannonbandmenu.diag

/** One SIM's last known widths, keyed by radio identity rather than carrier slot. */
internal class NrBandwidthHold {
    private data class Key(val arfcn: Int, val pci: Int)
    private data class Held(val band: Int?, val bandwidth: Double)
    private val held = object : LinkedHashMap<Key, Held>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Held>?) = size > 128
    }

    fun apply(carriers: List<DiagCarrier>): List<DiagCarrier> = carriers.map { carrier ->
        if (!carrier.nr || carrier.arfcn == null || carrier.pci == null) return@map carrier
        val key = Key(carrier.arfcn, carrier.pci)
        var previous = held[key]
        if (previous?.band != null && carrier.band != null && previous.band != carrier.band) {
            held.remove(key)
            previous = null
        }
        val measured = carrier.bandwidth?.takeIf { it.isFinite() && it > 0 && it <= 400 }
        if (measured != null) {
            held[key] = Held(carrier.band ?: previous?.band, measured)
        }
        carrier.withBandwidth(measured ?: previous?.bandwidth)
    }
}
