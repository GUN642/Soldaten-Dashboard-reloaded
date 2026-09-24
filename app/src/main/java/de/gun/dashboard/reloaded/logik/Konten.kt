package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.AppDaten
import de.gun.dashboard.reloaded.daten.MedEintrag
import de.gun.dashboard.reloaded.daten.neueIdLogik
import de.gun.dashboard.reloaded.daten.UrlaubZugang
import java.time.LocalDate

data class UrlaubStand(
    val anspruch: Double,
    val zugaenge: Double,
    val genommen: Double,
    val geplant: Double,
) {
    val rest get() = anspruch + zugaenge - genommen
    val restGeplant get() = rest - geplant
}

fun urlaubStand(d: AppDaten): UrlaubStand {
    val u = d.urlaub
    return UrlaubStand(
        u.jahresanspruch,
        u.zugaenge.sumOf { it.tage },
        u.zeitraeume.filter { it.status != "geplant" }.sumOf { it.tage },
        u.zeitraeume.filter { it.status == "geplant" }.sumOf { it.tage },
    )
}

fun mehrarbeitSaldo(d: AppDaten): Double = d.ueberstunden.startwert + d.ueberstunden.eintraege.sumOf { it.stunden }

/** Automatischer Zugang von 30 Tagen zu jedem neuen Jahr. */
fun jahreswechsel(d: AppDaten): AppDaten {
    val jahr = LocalDate.now().year
    val u = d.urlaub
    if (u.letztesJahr == null) return d.copy(urlaub = u.copy(letztesJahr = jahr))
    if (u.letztesJahr >= jahr) return d
    var letztes: Int = u.letztesJahr
    val neu = u.zugaenge.toMutableList()
    while (letztes < jahr) {
        letztes++
        neu += UrlaubZugang(neueIdLogik(), "01.01.$letztes", 30.0, "Jahreswechsel $letztes (automatisch)")
    }
    return d.copy(urlaub = u.copy(zugaenge = neu, letztesJahr = letztes))
}

/** Warnend (gelb) und abgelaufen (rot) über Lehrgänge, Dokumente und Akte. */
fun fristenZaehlen(d: AppDaten): Pair<Int, Int> {
    var w = 0
    var a = 0
    fun zaehle(bis: String) {
        when (statusVon(bis)) {
            Status.WARNUNG -> w++
            Status.ABGELAUFEN -> a++
            else -> {}
        }
    }
    d.ablaufregister.forEach { zaehle(it.gueltigBis) }
    d.dokumente.forEach { zaehle(it.gueltigBis) }
    (d.medizin.igf + d.medizin.iccs + d.medizin.avu + d.medizin.impfungen)
        .filter { it.gueltigBis.isNotBlank() }.forEach { zaehle(it.gueltigBis) }
    return w to a
}

/** IGF-Gesamtstatus aus den gewählten Pflichtbestandteilen. */
data class IgfStatus(val status: Status, val text: String, val fehlend: List<String>)

fun igfStatus(igf: List<MedEintrag>, pflicht: List<String>): IgfStatus {
    if (pflicht.isEmpty()) return IgfStatus(Status.UNBEKANNT, "Keine Pflichtbestandteile ausgewählt", emptyList())
    val fehlend = mutableListOf<String>()
    for (art in pflicht) {
        val neuester = igf.filter { it.art == art }.maxByOrNull { parseDE(it.datum) ?: LocalDate.MIN }
        if (neuester == null || neuester.gueltigBis.isBlank()) { fehlend += "$art (kein Nachweis)"; continue }
        if (statusVon(neuester.gueltigBis) == Status.ABGELAUFEN) fehlend += "$art (abgelaufen)"
    }
    return if (fehlend.isEmpty()) IgfStatus(Status.GUELTIG, "Alle Pflichtbestandteile aktuell", emptyList())
    else IgfStatus(Status.ABGELAUFEN, "${fehlend.size} von ${pflicht.size} fehlt bzw. abgelaufen", fehlend)
}

val IGF_ARTEN = listOf("BFT", "Leistungsmarsch", "Kleiderschwimmen", "Schießausbildung", "Sanitätsausbildung", "ABC-Selbst- und Kameradenhilfe")
