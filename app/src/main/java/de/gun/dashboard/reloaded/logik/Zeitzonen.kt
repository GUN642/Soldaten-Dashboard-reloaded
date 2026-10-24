package de.gun.dashboard.reloaded.logik

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Zeitzonen im Kalender. Angezeigt wird entweder immer deutsche Zeit (Europe/Berlin)
 * oder die aktuelle Ortszeit des Geräts – je nach Einstellung.
 */
object Zeitzonen {
    val DEUTSCHLAND: ZoneId = ZoneId.of("Europe/Berlin")

    /** "deutschland" oder "lokal"; wird beim Start und bei Änderungen aus den Einstellungen gesetzt. */
    @Volatile var modus: String = "deutschland"

    /** Zone, in der alle Uhrzeiten im Kalender angezeigt werden. */
    val anzeige: ZoneId get() = if (modus == "lokal") ZoneId.systemDefault() else DEUTSCHLAND

    /** Auswahl beim Anlegen eines Termins (häufige Einsatz-, Übungs- und Reiseorte). */
    val AUSWAHL: List<Pair<String, String>> = listOf(
        "Europe/Berlin" to "Deutschland",
        "UTC" to "UTC / Zulu",
        "Europe/London" to "Großbritannien",
        "Europe/Lisbon" to "Portugal",
        "Europe/Vilnius" to "Litauen",
        "Europe/Riga" to "Lettland",
        "Europe/Tallinn" to "Estland",
        "Europe/Helsinki" to "Finnland",
        "Europe/Bucharest" to "Rumänien",
        "Europe/Athens" to "Griechenland",
        "Europe/Istanbul" to "Türkei",
        "Asia/Jerusalem" to "Israel",
        "Asia/Amman" to "Jordanien",
        "Asia/Baghdad" to "Irak",
        "Asia/Qatar" to "Katar",
        "Asia/Dubai" to "VAE",
        "Asia/Kabul" to "Afghanistan",
        "Africa/Bamako" to "Mali",
        "Africa/Niamey" to "Niger",
        "Africa/Djibouti" to "Dschibuti",
        "America/New_York" to "USA Ost (New York)",
        "America/Chicago" to "USA Mitte (Chicago, Texas)",
        "America/Denver" to "USA Berge (Denver)",
        "America/Phoenix" to "USA Arizona",
        "America/Los_Angeles" to "USA West (Los Angeles)",
        "America/Anchorage" to "USA Alaska",
        "Pacific/Honolulu" to "USA Hawaii",
        "America/Toronto" to "Kanada Ost",
        "Asia/Tokyo" to "Japan",
        "Asia/Seoul" to "Südkorea",
        "Australia/Sydney" to "Australien Ost",
    )

    /** UTC-Versatz zum jetzigen Zeitpunkt, z. B. "UTC+2" oder "UTC−4:30". */
    fun versatz(zone: ZoneId, zeitpunkt: Instant = Instant.now()): String {
        val s = zone.rules.getOffset(zeitpunkt).totalSeconds
        if (s == 0) return "UTC"
        val h = Math.abs(s) / 3600
        val m = (Math.abs(s) % 3600) / 60
        return "UTC" + (if (s > 0) "+" else "−") + h + if (m > 0) ":%02d".format(m) else ""
    }

    /** Anzeigename einer Zone: eigener Name aus der Auswahl, sonst der Java-Name. */
    fun name(id: String?): String {
        if (id.isNullOrBlank()) return "Deutschland"
        AUSWAHL.firstOrNull { it.first == id }?.let { return it.second }
        return runCatching { ZoneId.of(id).getDisplayName(TextStyle.FULL, Locale.GERMAN) }.getOrDefault(id)
    }

    fun zoneOder(id: String?, ersatz: ZoneId = DEUTSCHLAND): ZoneId =
        id?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ersatz

    /** Wanduhrzeit aus Zone [von] in Zone [nach] umrechnen. */
    fun umrechnen(zeit: LocalDateTime, von: ZoneId, nach: ZoneId): LocalDateTime =
        if (von == nach) zeit else zeit.atZone(von).withZoneSameInstant(nach).toLocalDateTime()

    /** Haben zwei Zonen gerade dieselbe Uhrzeit (z. B. Berlin und Paris)? */
    fun gleicheUhrzeit(a: ZoneId, b: ZoneId, zeitpunkt: Instant = Instant.now()): Boolean =
        a.rules.getOffset(zeitpunkt) == b.rules.getOffset(zeitpunkt)
}
