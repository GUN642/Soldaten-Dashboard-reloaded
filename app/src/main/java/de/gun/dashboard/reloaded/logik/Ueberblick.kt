package de.gun.dashboard.reloaded.logik

import de.gun.dashboard.reloaded.daten.Tagesueberblick
import java.time.LocalDate
import java.time.LocalDateTime

/** Weckzeiten außerhalb dieses Fensters (Nachtdienst, Mittagsschlaf) gelten nicht als Aufstehen. */
private const val WECKER_AB = 4
private const val WECKER_BIS = 12

/**
 * Nächster Zeitpunkt für den Tagesüberblick: kurz nach dem Handy-Wecker, sonst zur festen Uhrzeit.
 * Ein Tag, an dem der Überblick schon gezeigt wurde, wird übersprungen.
 */
fun naechsterUeberblick(
    jetzt: LocalDateTime,
    e: Tagesueberblick,
    wecker: LocalDateTime?,
    zuletztGezeigt: LocalDate?,
): LocalDateTime? {
    if (!e.an || e.tage.isEmpty()) return null
    val fest = zeitAus(e.uhrzeit) ?: java.time.LocalTime.of(7, 0)
    for (v in 0L..8L) {
        val tag = jetzt.toLocalDate().plusDays(v)
        if (tag.dayOfWeek.value !in e.tage) continue
        if (tag == zuletztGezeigt) continue
        val nachWecker = e.modus == "wecker" && wecker != null && wecker.toLocalDate() == tag && wecker.hour in WECKER_AB until WECKER_BIS
        val zeit = if (nachWecker) wecker!!.plusMinutes(1) else tag.atTime(fest)
        if (zeit.isAfter(jetzt)) return zeit
    }
    return null
}
