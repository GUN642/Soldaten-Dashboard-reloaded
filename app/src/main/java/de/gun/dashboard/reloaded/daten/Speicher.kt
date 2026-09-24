package de.gun.dashboard.reloaded.daten

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val JsonFormat = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Hält den gesamten Datenbestand im Speicher und schreibt ihn bei jeder
 * Änderung (kurz gebündelt) atomar in files/daten.json. Die Android-
 * Systemsicherung erfasst diese Datei mit.
 */
object Speicher {
    private lateinit var datei: AtomicFile
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var schreibJob: Job? = null
    private val sperre = Any()

    private val _daten = MutableStateFlow(AppDaten())
    val daten: StateFlow<AppDaten> = _daten.asStateFlow()

    /** Wird nach jeder Änderung aufgerufen (Widget, Erinnerungen). */
    var beiAenderung: (() -> Unit)? = null

    val aktuell: AppDaten get() = _daten.value

    /** true, sobald beim Start bereits Daten vorlagen (für den Einrichtungsassistenten). */
    var hatteBestand = false
        private set

    @Volatile private var geladen = false

    fun init(context: Context) {
        synchronized(sperre) {
            if (geladen) return
            appContext = context.applicationContext
            datei = AtomicFile(File(appContext.filesDir, "daten.json"))
            val gelesen = try {
                if (datei.baseFile.exists()) {
                    val text = String(datei.readFully(), Charsets.UTF_8)
                    JsonFormat.decodeFromString(AppDaten.serializer(), text)
                } else null
            } catch (e: Exception) {
                null
            }
            hatteBestand = gelesen != null
            _daten.value = migrieren(gelesen ?: AppDaten())
            geladen = true
        }
    }

    fun aendern(block: (AppDaten) -> AppDaten) {
        _daten.update(block)
        schreibenPlanen()
        beiAenderung?.invoke()
    }

    /** Ersetzt den gesamten Bestand (Import). */
    fun ersetzen(neu: AppDaten) {
        _daten.value = migrieren(neu)
        sofortSchreiben()
        beiAenderung?.invoke()
    }

    private fun schreibenPlanen() {
        schreibJob?.cancel()
        schreibJob = scope.launch {
            delay(250)
            schreiben(_daten.value)
        }
    }

    /** Sofort schreiben, etwa wenn die App in den Hintergrund geht. */
    fun sofortSchreiben() {
        schreibJob?.cancel()
        schreiben(_daten.value)
    }

    private fun schreiben(stand: AppDaten) {
        synchronized(sperre) {
            if (!geladen) return
            val text = JsonFormat.encodeToString(AppDaten.serializer(), stand)
            val aus = try { datei.startWrite() } catch (e: Exception) { return }
            try {
                aus.write(text.toByteArray(Charsets.UTF_8))
                datei.finishWrite(aus)
            } catch (e: Exception) {
                datei.failWrite(aus)
            }
        }
    }

    /** Übernimmt Altlasten aus früheren Fassungen des Datenformats. */
    fun migrieren(d: AppDaten): AppDaten {
        var u = d.urlaub
        // Alte, getrennte "geplant"-Liste in die Hauptliste überführen
        if (u.geplant.isNotEmpty()) {
            u = u.copy(
                zeitraeume = u.zeitraeume + u.geplant.map { it.copy(status = "geplant") },
                geplant = emptyList()
            )
        }
        u = u.copy(zeitraeume = u.zeitraeume.map { if (it.status.isBlank()) it.copy(status = "eingetragen") else it })

        // Kalenderfarben lagen früher im Kalenderbereich
        val farben = d.kalender.farben.filterKeys { it !in d.farbenEigen } + d.farbenEigen

        val w = d.widget.copy(
            modus = if (d.widget.modus in listOf("dunkel", "hell")) d.widget.modus else "dunkel",
            tage = if (d.widget.tage in listOf(1, 2, 3, 7, 14)) d.widget.tage else 1,
            deckkraft = d.widget.deckkraft ?: 85,
        )
        val land = if (d.feiertagsLand.land in BUNDESLAENDER) d.feiertagsLand else FeiertagsLand()
        return d.copy(urlaub = u, farbenEigen = farben, widget = w, feiertagsLand = land)
    }
}

fun neueId(): String = UUID.randomUUID().toString()

val BUNDESLAENDER = linkedMapOf(
    "BW" to "Baden-Württemberg", "BY" to "Bayern", "BE" to "Berlin", "BB" to "Brandenburg",
    "HB" to "Bremen", "HH" to "Hamburg", "HE" to "Hessen", "MV" to "Mecklenburg-Vorpommern",
    "NI" to "Niedersachsen", "NW" to "Nordrhein-Westfalen", "RP" to "Rheinland-Pfalz",
    "SL" to "Saarland", "SN" to "Sachsen", "ST" to "Sachsen-Anhalt",
    "SH" to "Schleswig-Holstein", "TH" to "Thüringen"
)
