package fronsipswu.shannonbandmenu.diag

/** One SIM's CSI-RS port counts, following the network page's traffic/idle rules. */
internal class NrPortHold {
    private data class Key(val pci: Int, val channel: Int)
    private data class Held(val band: Int?, val ports: Int?, val confirmed: Boolean)
    private var held = emptyMap<Key, Held>()

    fun apply(carriers: List<DiagCarrier>): List<DiagCarrier> {
        val next = HashMap<Key, Held>()
        val result = carriers.map { carrier ->
            if (!carrier.nr || carrier.pci == null || carrier.arfcn == null) return@map carrier
            val key = Key(carrier.pci, carrier.arfcn)
            val previous = held[key]?.takeIf {
                it.band == null || carrier.band == null || it.band == carrier.band
            }
            val band = carrier.band ?: previous?.band
            // RX configuration can remain cached on Shannon while idle. Require
            // an actual PDSCH window as well, matching a live RX report's meaning.
            val current = when {
                carrier.downlinkScheduled && carrier.rx != null ->
                    Held(band, carrier.csiPorts, confirmed = carrier.csiPorts != null)
                previous?.confirmed == true -> previous.copy(band = band)
                else -> Held(band, listOfNotNull(previous?.ports, carrier.csiPorts).maxOrNull(), false)
            }
            next[key] = current
            carrier.withCsiPorts(current.ports)
        }
        held = next // Drop absent identities; role/slot moves alone keep the same key.
        return result
    }
}
