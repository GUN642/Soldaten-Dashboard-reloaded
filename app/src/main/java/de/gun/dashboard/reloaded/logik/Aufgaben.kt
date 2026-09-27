package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.Aufgabe
import de.gun.dashboard.reloaded.daten.neueIdLogik
import java.time.DayOfWeek
import java.time.LocalDate

/** Wiederholungsregeln für Aufgaben (Schlüssel wird gespeichert). */
val WIEDERHOLUNGEN = listOf(
    "" to "Keine",
    "taeglich" to "Täglich",
    "werktags" to "Werktags (Mo–Fr)",
    "woechentlich" to "Wöchentlich",
    "zweiwoechentlich" to "Alle 2 Wochen",
    "monatlich" to "Monatlich",
    "quartal" to "Vierteljährlich",
    "jaehrlich" to "Jährlich",
)

fun wiederholungText(regel: String): String = WIEDERHOLUNGEN.firstOrNull { it.first == regel }?.second ?: ""

private fun schritt(d: LocalDate, regel: String): LocalDate? = when (regel) {
    "taeglich" -> d.plusDays(1)
    "werktags" -> {
        var n = d.plusDays(1)
        while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
        n
    }
    "woechentlich" -> d.plusWeeks(1)
    "zweiwoechentlich" -> d.plusWeeks(2)
    // plusMonths behält den Tag bzw. nimmt den Monatsletzten (31.01. -> 28./29.02.)
    "monatlich" -> d.plusMonths(1)
    "quartal" -> d.plusMonths(3)
    "jaehrlich" -> d.plusYears(1)
    else -> null
}

/**
 * Nächste Fälligkeit nach [basis]. Liegt sie noch in der Vergangenheit (Aufgabe spät erledigt),
 * wird weitergezählt, bis sie heute oder später ist – verpasste Termine werden nicht nachgeholt.
 */
fun naechsteFaelligkeit(basis: LocalDate, regel: String, heute: LocalDate): LocalDate? {
    var n = schritt(basis, regel) ?: return null
    var sicherung = 0
    while (n.isBefore(heute) && sicherung++ < 5000) n = schritt(n, regel) ?: return null
    return n
}

/**
 * Aufgabe erledigt/offen umschalten. Wird eine wiederkehrende Aufgabe erledigt, entsteht sofort
 * die nächste Aufgabe mit neuem Fälligkeitsdatum; Anhänge wandern zur neuen Aufgabe.
 */
fun aufgabeUmschalten(liste: List<Aufgabe>, id: String, heute: LocalDate, neueId: () -> String = ::neueIdLogik): List<Aufgabe> {
    val t = liste.firstOrNull { it.id == id } ?: return liste
    if (t.erledigt) return liste.map { if (it.id == id) it.copy(erledigt = false, erledigtAm = "") else it }
    val heuteText = heute.alsDE()
    val naechste = if (t.wiederholung.isBlank()) null
    else naechsteFaelligkeit(parseDE(t.faellig) ?: heute, t.wiederholung, heute)
    if (naechste == null) return liste.map { if (it.id == id) it.copy(erledigt = true, erledigtAm = heuteText) else it }
    val neu = t.copy(id = neueId(), faellig = naechste.alsDE(), erledigt = false, erledigtAm = "", erstellt = heuteText)
    return liste.flatMap {
        if (it.id == id) listOf(it.copy(erledigt = true, erledigtAm = heuteText, anhaenge = emptyList()), neu) else listOf(it)
    }
}
