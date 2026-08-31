package fronsipswu.shannonbandmenu

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object BandPreferences {
    private val RAT_SIM1 = stringPreferencesKey("rat_sim1")
    private val GSM_SIM1 = stringSetPreferencesKey("gsm_sim1")
    private val WCDMA_SIM1 = stringSetPreferencesKey("wcdma_sim1")
    private val LTE_SIM1 = stringSetPreferencesKey("lte_sim1")
    private val NR_NSA_SIM1 = stringSetPreferencesKey("nr_nsa_sim1")
    private val NR_SA_SIM1 = stringSetPreferencesKey("nr_sa_sim1")
    private val NR_MODE_SIM1 = stringPreferencesKey("nr_mode_sim1")

    private val RAT_SIM2 = stringPreferencesKey("rat_sim2")
    private val GSM_SIM2 = stringSetPreferencesKey("gsm_sim2")
    private val WCDMA_SIM2 = stringSetPreferencesKey("wcdma_sim2")
    private val LTE_SIM2 = stringSetPreferencesKey("lte_sim2")
    private val NR_NSA_SIM2 = stringSetPreferencesKey("nr_nsa_sim2")
    private val NR_SA_SIM2 = stringSetPreferencesKey("nr_sa_sim2")
    private val NR_MODE_SIM2 = stringPreferencesKey("nr_mode_sim2")
    private val PROFILE_SAVED_SIM1 = booleanPreferencesKey("profile_saved_sim1")
    private val PROFILE_SAVED_SIM2 = booleanPreferencesKey("profile_saved_sim2")
    private val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
    private val NR_INDEPENDENT_SUPPORTED = booleanPreferencesKey("nr_independent_supported")
    private val VISIBLE_GSM_BANDS = stringSetPreferencesKey("visible_gsm_bands")
    private val VISIBLE_WCDMA_BANDS = stringSetPreferencesKey("visible_wcdma_bands")
    private val VISIBLE_LTE_BANDS = stringSetPreferencesKey("visible_lte_bands")
    private val VISIBLE_NR_SA_BANDS = stringSetPreferencesKey("visible_nr_sa_bands")
    private val VISIBLE_NR_NSA_BANDS = stringSetPreferencesKey("visible_nr_nsa_bands")

    fun getSimState(dataStore: DataStore<Preferences>, sim: Int): Flow<SimState?> {
        return dataStore.data.map { prefs ->
            val saved = if (sim == 1) prefs[PROFILE_SAVED_SIM1] == true
                else prefs[PROFILE_SAVED_SIM2] == true
            if (!saved) return@map null
            if (sim == 1) prefsToSimState(
                prefs[RAT_SIM1],
                prefs[GSM_SIM1],
                prefs[WCDMA_SIM1],
                prefs[LTE_SIM1],
                prefs[NR_NSA_SIM1],
                prefs[NR_SA_SIM1],
                prefs[NR_MODE_SIM1]
            ) else prefsToSimState(
                prefs[RAT_SIM2],
                prefs[GSM_SIM2],
                prefs[WCDMA_SIM2],
                prefs[LTE_SIM2],
                prefs[NR_NSA_SIM2],
                prefs[NR_SA_SIM2],
                prefs[NR_MODE_SIM2]
            )
        }
    }

    suspend fun saveSimState(dataStore: DataStore<Preferences>, sim: Int, state: SimState) {
        dataStore.edit { prefs ->
            if (sim == 1) {
                prefs[PROFILE_SAVED_SIM1] = true
                prefs[RAT_SIM1] = state.ratMask.joinToString(",") { it.name }
                prefs[GSM_SIM1] = state.gsmBands.map { it.toString() }.toSet()
                prefs[WCDMA_SIM1] = state.wcdmaBands.map { it.toString() }.toSet()
                prefs[LTE_SIM1] = state.lteBands.map { it.toString() }.toSet()
                prefs[NR_NSA_SIM1] = state.nrNsaBands.map { it.toString() }.toSet()
                prefs[NR_SA_SIM1] = state.nrSaBands.map { it.toString() }.toSet()
                prefs[NR_MODE_SIM1] = state.nrMode.name
            } else {
                prefs[PROFILE_SAVED_SIM2] = true
                prefs[RAT_SIM2] = state.ratMask.joinToString(",") { it.name }
                prefs[GSM_SIM2] = state.gsmBands.map { it.toString() }.toSet()
                prefs[WCDMA_SIM2] = state.wcdmaBands.map { it.toString() }.toSet()
                prefs[LTE_SIM2] = state.lteBands.map { it.toString() }.toSet()
                prefs[NR_NSA_SIM2] = state.nrNsaBands.map { it.toString() }.toSet()
                prefs[NR_SA_SIM2] = state.nrSaBands.map { it.toString() }.toSet()
                prefs[NR_MODE_SIM2] = state.nrMode.name
            }
        }
    }

    private fun prefsToSimState(
        rat: String?, gsm: Set<String>?, wcdma: Set<String>?,
        lte: Set<String>?, nrNsa: Set<String>?, nrSa: Set<String>?,
        nrMode: String?
    ): SimState {
        return SimState(
            ratMask = rat?.split(",")?.mapNotNull { name ->
                runCatching { RatType.valueOf(name.trim()) }.getOrNull()
            }?.toSet() ?: emptySet(),
            gsmBands = gsm?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            wcdmaBands = wcdma?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            lteBands = lte?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            nrNsaBands = nrNsa?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            nrSaBands = nrSa?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
            nrMode = nrMode?.let { runCatching { NrMode.valueOf(it) }.getOrNull() } ?: NrMode.BOTH
        )
    }

    fun getDebugLogging(dataStore: DataStore<Preferences>): Flow<Boolean> {
        return dataStore.data.map { it[DEBUG_LOGGING] ?: false }
    }

    suspend fun setDebugLogging(dataStore: DataStore<Preferences>, enabled: Boolean) {
        dataStore.edit { it[DEBUG_LOGGING] = enabled }
    }

    fun getNrIndependentSupported(dataStore: DataStore<Preferences>): Flow<Boolean?> {
        return dataStore.data.map { it[NR_INDEPENDENT_SUPPORTED] }
    }

    suspend fun setNrIndependentSupported(dataStore: DataStore<Preferences>, supported: Boolean) {
        dataStore.edit { it[NR_INDEPENDENT_SUPPORTED] = supported }
    }

    fun getBandDisplayPreferences(dataStore: DataStore<Preferences>): Flow<BandDisplayPreferences> {
        return dataStore.data.map { prefs ->
            BandDisplayPreferences(
                gsm = prefs[VISIBLE_GSM_BANDS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                wcdma = prefs[VISIBLE_WCDMA_BANDS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                lte = prefs[VISIBLE_LTE_BANDS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                nrSa = prefs[VISIBLE_NR_SA_BANDS]?.mapNotNull { it.toIntOrNull() }?.toSet(),
                nrNsa = prefs[VISIBLE_NR_NSA_BANDS]?.mapNotNull { it.toIntOrNull() }?.toSet()
            )
        }
    }

    /** Pass null for a family to restore its default (show all) behavior. */
    suspend fun setBandDisplayPreferences(
        dataStore: DataStore<Preferences>,
        gsm: Set<Int>?,
        wcdma: Set<Int>?,
        lte: Set<Int>?,
        nrSa: Set<Int>?,
        nrNsa: Set<Int>?
    ) {
        dataStore.edit { prefs ->
            if (gsm == null) prefs.remove(VISIBLE_GSM_BANDS)
            else prefs[VISIBLE_GSM_BANDS] = gsm.map { it.toString() }.toSet()
            if (wcdma == null) prefs.remove(VISIBLE_WCDMA_BANDS)
            else prefs[VISIBLE_WCDMA_BANDS] = wcdma.map { it.toString() }.toSet()
            if (lte == null) prefs.remove(VISIBLE_LTE_BANDS)
            else prefs[VISIBLE_LTE_BANDS] = lte.map { it.toString() }.toSet()
            if (nrSa == null) prefs.remove(VISIBLE_NR_SA_BANDS)
            else prefs[VISIBLE_NR_SA_BANDS] = nrSa.map { it.toString() }.toSet()
            if (nrNsa == null) prefs.remove(VISIBLE_NR_NSA_BANDS)
            else prefs[VISIBLE_NR_NSA_BANDS] = nrNsa.map { it.toString() }.toSet()
        }
    }
}
