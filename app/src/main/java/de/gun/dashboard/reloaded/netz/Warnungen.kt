package de.gun.dashboard.reloaded.netz

import de.gun.dashboard.reloaded.daten.Ort
import kotlinx.serialization.json.jsonObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------- Amtliche Warnungen des Deutschen Wetterdienstes (DWD) ----------------

/** Eine DWD-Warnung für die Gemeinde des eingestellten Ortes. */
data class DwdWarnung(
    val id: String,
    val ereignis: String,
    val stufe: Int,
    val gruppen: List<String>,
    val ueberschrift: String,
    val beschreibung: String,
    val hinweise: String,
    val beginn: OffsetDateTime?,
    val ende: OffsetDateTime?,
    val gebiet: String,
) {
    val stufeText: String get() = when (stufe) {
        4 -> "Extremes Unwetter"
        3 -> "Unwetterwarnung"
        2 -> "Markante Wetterwarnung"
        else -> "Wetterwarnung"
    }

    /** Unwetter im engeren Sinn: Gewitter, Sturm, Orkan, Tornado, Hagel, Starkregen. */
    val istUnwetterArt: Boolean get() = gruppen.any { it in UNWETTER_GRUPPEN }

    fun zeitraum(): String {
        val f = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", java.util.Locale.GERMAN)
        val b = beginn?.atZoneSameInstant(ZoneId.systemDefault())?.format(f)
        val e = ende?.atZoneSameInstant(ZoneId.systemDefault())?.format(f)
        return when {
            b != null && e != null -> "$b – $e"
            b != null -> "ab $b"
            e != null -> "bis $e"
            else -> ""
        }
    }
}

private val UNWETTER_GRUPPEN = setOf("THUNDERSTORM", "WIND", "TORNADO", "HAIL", "RAIN")

object DwdWarnDienst {
    /** Zuletzt geladene Warnungen (null = noch nicht geladen). Wird von HEUTE und Android Auto gelesen. */
    val aktuell = kotlinx.coroutines.flow.MutableStateFlow<List<DwdWarnung>?>(null)

    private var zwischen: Pair<String, Pair<Long, List<DwdWarnung>>>? = null

    /**
     * Warnungen für die Gemeinde, in der der Ort liegt (DWD-GeoServer, Ebene Warnungen_Gemeinden).
     * Beide Achsenreihenfolgen werden abgefragt; die vertauschte liegt immer außerhalb Deutschlands.
     */
    suspend fun laden(ort: Ort, neu: Boolean = false): List<DwdWarnung> {
        val schluessel = "${ort.breite},${ort.laenge}"
        zwischen?.let { (k, v) ->
            if (!neu && k == schluessel && System.currentTimeMillis() - v.first < 5 * 60_000) return v.second
        }
        val b = ort.breite; val l = ort.laenge
        val filter = "CONTAINS(THE_GEOM,POINT($b $l)) OR CONTAINS(THE_GEOM,POINT($l $b))"
        val url = "https://maps.dwd.de/geoserver/dwd/ows?service=WFS&version=2.0.0&request=GetFeature" +
            "&typeName=dwd:Warnungen_Gemeinden&outputFormat=application/json" +
            "&propertyName=IDENTIFIER,EVENT,SEVERITY,EC_GROUP,HEADLINE,DESCRIPTION,INSTRUCTION,ONSET,EXPIRES,AREADESC,NAME,MSGTYPE" +
            "&CQL_FILTER=" + Netz.enc(filter)
        val j = Netz.json(url).jsonObject
        val liste = j["features"].arr().orEmpty().mapNotNull { f ->
            val pr = f.obj()?.get("properties").obj() ?: return@mapNotNull null
            if ((pr["MSGTYPE"].s() ?: "").equals("Cancel", true)) return@mapNotNull null
            fun zeit(k: String) = pr[k].s()?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            DwdWarnung(
                id = pr["IDENTIFIER"].s() ?: "",
                ereignis = (pr["EVENT"].s() ?: "").lowercase().replaceFirstChar { it.uppercase() },
                stufe = when ((pr["SEVERITY"].s() ?: "").lowercase()) {
                    "extreme" -> 4; "severe" -> 3; "moderate" -> 2; else -> 1
                },
                gruppen = (pr["EC_GROUP"].s() ?: "").split(';', ',').map { it.trim().uppercase() }.filter { it.isNotEmpty() },
                ueberschrift = pr["HEADLINE"].s() ?: "",
                beschreibung = pr["DESCRIPTION"].s() ?: "",
                hinweise = pr["INSTRUCTION"].s() ?: "",
                beginn = zeit("ONSET"),
                ende = zeit("EXPIRES"),
                gebiet = pr["AREADESC"].s() ?: pr["NAME"].s() ?: "",
            )
        }
            // abgelaufene Warnungen ausblenden, gleiche Meldung nur einmal
            .filter { w -> w.ende?.isAfter(OffsetDateTime.now()) ?: true }
            .distinctBy { it.id.ifBlank { it.ueberschrift + it.beginn } }
            .sortedWith(compareByDescending<DwdWarnung> { it.stufe }.thenBy { it.beginn })
        zwischen = schluessel to (System.currentTimeMillis() to liste)
        aktuell.value = liste
        return liste
    }

    /** Soll zu dieser Warnung eine Benachrichtigung kommen? Unwetterarten ab Stufe 2, alles andere ab Stufe 3. */
    fun meldenswert(w: DwdWarnung): Boolean = w.stufe >= 3 || (w.stufe >= 2 && w.istUnwetterArt)

    /** Farben der DWD-Warnstufen (gelb, orange, rot, violett). */
    fun farbe(stufe: Int): Long = when (stufe) {
        4 -> 0xFFAF40C8
        3 -> 0xFFE8322B
        2 -> 0xFFFF8A00
        else -> 0xFFFFD200
    }
}
