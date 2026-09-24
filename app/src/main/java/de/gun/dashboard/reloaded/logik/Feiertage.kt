package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.Ferien
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

data class Feiertag(val name: String, val datum: LocalDate)

object Feiertage {
    /** Gaußsche Osterformel (Meeus/Jones/Butcher). */
    fun ostersonntag(jahr: Int): LocalDate {
        val a = jahr % 19
        val b = jahr / 100
        val c = jahr % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monat = (h + l - 7 * m + 114) / 31
        val tag = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(jahr, monat, tag)
    }

    /** Buß- und Bettag: Mittwoch vor dem 23. November. */
    private fun bussUndBettag(jahr: Int): LocalDate {
        var d = LocalDate.of(jahr, 11, 22)
        while (d.dayOfWeek != DayOfWeek.WEDNESDAY) d = d.minusDays(1)
        return d
    }

    private val cache = ConcurrentHashMap<String, List<Feiertag>>()

    fun liste(jahr: Int, land: String): List<Feiertag> = cache.getOrPut("$jahr|$land") {
        val o = ostersonntag(jahr)
        val alle = listOf(
            Triple("Neujahr", LocalDate.of(jahr, 1, 1), null),
            Triple("Heilige Drei Könige", LocalDate.of(jahr, 1, 6), listOf("BW", "BY", "ST")),
            Triple("Internationaler Frauentag", LocalDate.of(jahr, 3, 8), listOf("BE", "MV")),
            Triple("Karfreitag", o.minusDays(2), null),
            Triple("Ostersonntag", o, listOf("BB")),
            Triple("Ostermontag", o.plusDays(1), null),
            Triple("Tag der Arbeit", LocalDate.of(jahr, 5, 1), null),
            Triple("Christi Himmelfahrt", o.plusDays(39), null),
            Triple("Pfingstsonntag", o.plusDays(49), listOf("BB")),
            Triple("Pfingstmontag", o.plusDays(50), null),
            Triple("Fronleichnam", o.plusDays(60), listOf("BW", "BY", "HE", "NW", "RP", "SL")),
            Triple("Mariä Himmelfahrt", LocalDate.of(jahr, 8, 15), listOf("BY", "SL")),
            Triple("Weltkindertag", LocalDate.of(jahr, 9, 20), listOf("TH")),
            Triple("Tag der Deutschen Einheit", LocalDate.of(jahr, 10, 3), null),
            Triple("Reformationstag", LocalDate.of(jahr, 10, 31),
                listOf("BB", "HB", "HH", "MV", "NI", "SN", "ST", "SH", "TH")),
            Triple("Allerheiligen", LocalDate.of(jahr, 11, 1), listOf("BW", "BY", "NW", "RP", "SL")),
            Triple("Buß- und Bettag", bussUndBettag(jahr), listOf("SN")),
            Triple("1. Weihnachtsfeiertag", LocalDate.of(jahr, 12, 25), null),
            Triple("2. Weihnachtsfeiertag", LocalDate.of(jahr, 12, 26), null),
        )
        alle.filter { it.third == null || land in it.third!! }.map { Feiertag(it.first, it.second) }
    }

    fun name(d: LocalDate, land: String): String? = liste(d.year, land).firstOrNull { it.datum == d }?.name

    fun ist(d: LocalDate, land: String): Boolean = name(d, land) != null

    fun imZeitraum(von: LocalDate, bis: LocalDate, land: String): List<Feiertag> {
        if (bis.isBefore(von)) return emptyList()
        return (von.year..bis.year).flatMap { liste(it, land) }
            .filter { !it.datum.isBefore(von) && !it.datum.isAfter(bis) }
            .sortedBy { it.datum }
    }

    fun beschriftung(von: LocalDate, bis: LocalDate, land: String): String =
        imZeitraum(von, bis, land).joinToString(", ") { it.name + " (" + it.datum.alsDE() + ")" }

    /** Werktage Mo–Fr ohne Feiertage des Landes. */
    fun arbeitstage(von: LocalDate?, bis: LocalDate?, land: String): Int {
        if (von == null || bis == null || bis.isBefore(von)) return 0
        var n = 0
        var d = von
        while (!d.isAfter(bis)) {
            if (!istWochenende(d) && !ist(d, land)) n++
            d = d.plusDays(1)
        }
        return n
    }

    /** Ferienname für einen Tag, sonst null. */
    fun ferien(d: LocalDate, ferien: Ferien): String? {
        if (!ferien.an) return null
        val liste = ferien.jahre[d.year.toString()] ?: return null
        for (a in liste) {
            val von = runCatching { LocalDate.parse(a.von.take(10)) }.getOrNull() ?: continue
            val bis = runCatching { LocalDate.parse(a.bis.take(10)) }.getOrNull() ?: continue
            if (!d.isBefore(von) && !d.isAfter(bis)) return a.name
        }
        return null
    }
}
